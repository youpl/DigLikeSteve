package io.github.youpl.diglikesteve.platform.paper.player;

import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;
import io.github.youpl.diglikesteve.core.shared.WorldLocation;
import io.github.youpl.diglikesteve.platform.paper.inventory.PaperInventoryCustodyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Actions joueur (DAT §10.5). Les téléportations utilisent {@code teleportAsync} pour éviter
 * tout chargement synchrone de chunk, et leur {@code Future} n'est jamais bloqué : les
 * continuations qui modifient le joueur repassent par son {@code EntityScheduler}.
 */
public final class PaperPlayerAdapter implements PlayerPort {

    private final Plugin plugin;
    private final PaperInventoryCustodyAdapter custody;
    private final AtomicReference<WorldLocation> exitDestination = new AtomicReference<>();

    /** Joueurs figés pendant la phase d'inscription. */
    private final Set<PlayerId> frozen = ConcurrentHashMap.newKeySet();

    public PaperPlayerAdapter(Plugin plugin, PaperInventoryCustodyAdapter custody) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.custody = Objects.requireNonNull(custody, "custody");
    }

    public void exitDestination(WorldLocation destination) {
        exitDestination.set(destination);
    }

    public boolean isFrozen(PlayerId playerId) {
        return frozen.contains(playerId);
    }

    @Override
    public boolean isOnline(PlayerId playerId) {
        Player player = Bukkit.getPlayer(playerId.value());
        return player != null && player.isOnline();
    }

    @Override
    public Optional<String> nameOf(PlayerId playerId) {
        Player player = Bukkit.getPlayer(playerId.value());
        if (player != null) {
            return Optional.of(player.getName());
        }
        return Optional.ofNullable(Bukkit.getOfflinePlayer(playerId.value()).getName());
    }

    @Override
    public CompletableFuture<Boolean> applyKit(PlayerId playerId, SessionId sessionId) {
        return custody.applyKit(playerId, sessionId).exceptionally(failure -> Boolean.FALSE);
    }

    @Override
    public CompletableFuture<Boolean> stripSessionItems(PlayerId playerId, SessionId sessionId) {
        return custody.stripSessionItems(playerId).exceptionally(failure -> Boolean.FALSE);
    }

    @Override
    public CompletableFuture<Boolean> teleport(PlayerId playerId, String worldName, BlockPos target, float yaw) {
        World world = Bukkit.getWorld(worldName);
        Player player = Bukkit.getPlayer(playerId.value());
        if (world == null || player == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        Location destination = new Location(world,
                target.x() + 0.5d, target.y(), target.z() + 0.5d, yaw, 0.0f);
        return player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .exceptionally(failure -> Boolean.FALSE);
    }

    @Override
    public CompletableFuture<Boolean> teleportToExit(PlayerId playerId) {
        WorldLocation destination = exitDestination.get();
        Player player = Bukkit.getPlayer(playerId.value());
        if (player == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        Location target = resolveExit(destination, player);
        if (target == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .exceptionally(failure -> Boolean.FALSE);
    }

    private Location resolveExit(WorldLocation destination, Player player) {
        if (destination != null) {
            World world = Bukkit.getWorld(destination.worldName());
            if (world != null) {
                return new Location(world, destination.x(), destination.y(), destination.z(),
                        destination.yaw(), destination.pitch());
            }
        }
        // Repli natif : le spawn du monde principal, jamais une position d'arène.
        World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        return fallback == null ? null : fallback.getSpawnLocation();
    }

    @Override
    public CompletableFuture<Void> preloadDestination(String worldName, BlockPos target) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        return world.getChunkAtAsync(target.chunkX(), target.chunkZ(), true).thenApply(chunk -> null);
    }

    @Override
    public CompletableFuture<Boolean> freeze(PlayerId playerId, boolean shouldFreeze) {
        if (shouldFreeze) {
            frozen.add(playerId);
        } else {
            frozen.remove(playerId);
        }
        Player player = Bukkit.getPlayer(playerId.value());
        if (player == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        player.getScheduler().execute(plugin, () -> {
            player.setGameMode(GameMode.SURVIVAL);
            done.complete(Boolean.TRUE);
        }, () -> done.complete(Boolean.FALSE), 1L);
        return done;
    }

    @Override
    public boolean hasPermission(PlayerId playerId, String permission) {
        Player player = Bukkit.getPlayer(playerId.value());
        return player != null && player.hasPermission(permission);
    }

    public void clearFrozen() {
        frozen.clear();
    }
}
