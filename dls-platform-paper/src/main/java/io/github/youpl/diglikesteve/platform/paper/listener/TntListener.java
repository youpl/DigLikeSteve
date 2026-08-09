package io.github.youpl.diglikesteve.platform.paper.listener;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.configuration.model.TntSettings;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Attribution et comptabilité des TNT (DAT §7.2), sans NMS ni couche paquet.
 *
 * <p>Le chaînage se fait en trois temps : {@code TNTPrimeEvent} retient le propriétaire pour la
 * position amorcée, {@code EntitySpawnEvent} l'inscrit sur l'entité créée via un PDC et dans le
 * registre court terme, puis {@code EntityExplodeEvent} additionne les blocs consommés.</p>
 *
 * <p>{@code TNTPrimed#getSource()} sert de secours mais ne suffit pas seul : sa source peut
 * devenir nulle après un déchargement de chunk.</p>
 */
public final class TntListener implements Listener {

    /** Durée de vie d'une intention d'amorçage en attente d'apparition de l'entité. */
    private static final long PENDING_TTL_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final DlsServices services;
    private final Map<Long, PendingPrime> pendingPrimes = new ConcurrentHashMap<>();

    public TntListener(DlsServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    private TntSettings settings() {
        return services.lifecycle().config().tnt();
    }

    /** Retient l'intention d'amorçage pour la position concernée. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent event) {
        GameSession session = services.lifecycle().session().orElse(null);
        if (session == null || !session.phase().acceptsScoring() || !isInArena(session, event.getBlock())) {
            return;
        }

        TntSettings settings = settings();
        Entity primer = event.getPrimingEntity();

        PlayerId owner = switch (event.getCause()) {
            case PLAYER -> resolvePlayerOwner(session, settings, primer);
            case EXPLOSION -> resolveChainedOwner(primer);
            default -> settings.allows(event.getCause().name()) ? resolvePlayerOwner(session, settings, primer) : null;
        };

        if (owner == null) {
            // Une TNT amorcée par redstone, feu, projectile ou plugin tiers ne rapporte rien.
            return;
        }
        Block block = event.getBlock();
        pendingPrimes.put(pack(block.getX(), block.getY(), block.getZ()),
                new PendingPrime(owner, services.clock().nanoTime()));
        purgePending();
    }

    private PlayerId resolvePlayerOwner(GameSession session, TntSettings settings, Entity primer) {
        if (!(primer instanceof Player player)) {
            return null;
        }
        PlayerId playerId = PlayerId.of(player.getUniqueId());
        if (session.participant(playerId).filter(p -> p.status().canScore()).isEmpty()) {
            return null;
        }
        if (settings.requireDlsIgniter()
                && !services.kits().isIgniter(player.getInventory().getItemInMainHand(), session.id())
                && !services.kits().isIgniter(player.getInventory().getItemInOffHand(), session.id())) {
            return null;
        }
        return playerId;
    }

    /** Réaction en chaîne : l'identité vient de la TNT source. */
    private PlayerId resolveChainedOwner(Entity primer) {
        if (!(primer instanceof TNTPrimed source)) {
            return null;
        }
        return ownerOf(source).orElse(null);
    }

    /** Inscrit le propriétaire sur l'entité dès son apparition. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        Location location = tnt.getLocation();
        long key = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        PendingPrime pending = pendingPrimes.remove(key);
        if (pending == null) {
            return;
        }
        tnt.getPersistentDataContainer().set(services.keys().tntOwner(), PersistentDataType.STRING,
                pending.owner().toString());
        services.tnt().onPrimed(new Commands.TntPrimed(tnt.getUniqueId(), pending.owner()));
    }

    /** Comptabilise les blocs détruits et confine les dégâts à l'arène. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        GameSession session = services.lifecycle().session().orElse(null);
        if (session == null) {
            return;
        }
        ArenaBounds bounds = session.arena().map(arena -> arena.bounds()).orElse(null);
        if (bounds == null) {
            return;
        }

        TntSettings settings = settings();
        boolean sameWorld = bounds.worldName().equals(event.getLocation().getWorld().getName());

        List<BlockPos> destroyedInArena = new ArrayList<>();
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            boolean inside = sameWorld && bounds.contains(block.getX(), block.getY(), block.getZ());
            if (inside) {
                destroyedInArena.add(new BlockPos(block.getX(), block.getY(), block.getZ()));
            } else if (settings.confineDamageToArena()) {
                // Aucun dommage de bloc en dehors du cuboïde.
                iterator.remove();
            }
        }

        if (settings.removeExplosionDrops()) {
            event.setYield(0.0f);
        }
        if (destroyedInArena.isEmpty()) {
            return;
        }

        ensureRegistered(tnt);
        services.tnt().onExplosion(session, new Commands.ExplosionBlocks(tnt.getUniqueId(), destroyedInArena));
    }

    /** Réinscrit une TNT dont le registre aurait expiré, en relisant son PDC. */
    private void ensureRegistered(TNTPrimed tnt) {
        ownerOf(tnt).ifPresent(owner ->
                services.tnt().onPrimed(new Commands.TntPrimed(tnt.getUniqueId(), owner)));
    }

    /** Propriétaire d'une TNT : PDC d'abord, puis secours par {@code getSource()}. */
    public Optional<PlayerId> ownerOf(TNTPrimed tnt) {
        String raw = tnt.getPersistentDataContainer().get(services.keys().tntOwner(), PersistentDataType.STRING);
        if (raw != null) {
            try {
                return Optional.of(PlayerId.parse(raw));
            } catch (IllegalArgumentException malformed) {
                services.log().warn("PDC de propriété TNT illisible", "tnt", tnt.getUniqueId());
            }
        }
        Entity source = tnt.getSource();
        if (source instanceof Player player) {
            return Optional.of(PlayerId.of(player.getUniqueId()));
        }
        if (source instanceof TNTPrimed parent && !parent.getUniqueId().equals(tnt.getUniqueId())) {
            return ownerOf(parent);
        }
        return Optional.empty();
    }

    private boolean isInArena(GameSession session, Block block) {
        return session.arena()
                .map(arena -> arena.bounds())
                .filter(bounds -> bounds.worldName().equals(block.getWorld().getName()))
                .filter(bounds -> bounds.contains(block.getX(), block.getY(), block.getZ()))
                .isPresent();
    }

    /** Purge des intentions d'amorçage restées sans entité (DAT §15). */
    private void purgePending() {
        long now = services.clock().nanoTime();
        pendingPrimes.entrySet().removeIf(entry -> now - entry.getValue().createdAtNanos() >= PENDING_TTL_NANOS);
    }

    public void clear() {
        pendingPrimes.clear();
    }

    public int pendingPrimes() {
        return pendingPrimes.size();
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FF_FFFFL) << 38 | ((long) z & 0x3FF_FFFFL) << 12 | ((long) y & 0xFFFL);
    }

    private record PendingPrime(PlayerId owner, long createdAtNanos) {
    }

    /** Identifiant d'entité, exposé aux tests et au diagnostic. */
    public static UUID idOf(Entity entity) {
        return entity.getUniqueId();
    }
}
