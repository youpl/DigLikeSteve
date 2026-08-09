package io.github.youpl.diglikesteve.platform.paper.listener;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.game.GameSettings;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Politique de dégâts et pénalité TNT (DAT §2 et §7.3).
 *
 * <p>Recommandation appliquée par défaut : PvP désactivé, dégâts environnementaux annulés,
 * dégâts TNT conservés mais non létaux — la pénalité de score reste la sanction principale.</p>
 */
public final class DamageListener implements Listener {

    private final DlsServices services;
    private final TntListener tntListener;

    public DamageListener(DlsServices services, TntListener tntListener) {
        this.services = Objects.requireNonNull(services, "services");
        this.tntListener = Objects.requireNonNull(tntListener, "tntListener");
    }

    /** Applique la politique de dégâts avant que les autres plugins ne décident. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamagePolicy(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        GameSession session = services.lifecycle().session().orElse(null);
        if (session == null) {
            return;
        }
        PlayerId playerId = PlayerId.of(player.getUniqueId());
        if (session.participant(playerId).isEmpty()) {
            return;
        }

        GameSettings settings = session.settings();
        boolean fromTnt = isTntDamage(event);

        if (!fromTnt) {
            if (isPvp(event) && !settings.pvpEnabled()) {
                event.setCancelled(true);
                return;
            }
            if (settings.cancelEnvironmentalDamage()) {
                event.setCancelled(true);
                return;
            }
        }

        if (fromTnt && !settings.tntDamageLethal()) {
            // La TNT blesse mais ne tue pas : on laisse toujours un demi-cœur.
            double remaining = player.getHealth() - event.getFinalDamage();
            if (remaining <= 0.0d) {
                double survivable = Math.max(0.0d, player.getHealth() - 1.0d);
                event.setDamage(survivable);
            }
        }
    }

    /**
     * Comptabilise les dégâts finaux au niveau {@code MONITOR} : l'événement n'est plus
     * modifiable et sa valeur définitive est connue.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageAccounting(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getDamager() instanceof TNTPrimed tnt)) {
            return;
        }
        Optional<PlayerId> owner = tntListener.ownerOf(tnt);
        if (owner.isEmpty()) {
            return;
        }
        PlayerId victim = PlayerId.of(player.getUniqueId());
        if (!owner.get().equals(victim)) {
            // Seul le propriétaire touché par sa propre TNT est pénalisé : un adversaire ne peut
            // pas faire perdre de points à une victime.
            return;
        }
        services.tnt().onOwnerDamaged(new Commands.OwnerDamaged(tnt.getUniqueId(), victim, event.getFinalDamage()));
    }

    private static boolean isTntDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return false;
        }
        return !(event instanceof EntityDamageByEntityEvent byEntity)
                || byEntity.getDamager() instanceof TNTPrimed;
    }

    private static boolean isPvp(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player;
    }
}
