package io.github.youpl.diglikesteve.integration.warp;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.configuration.model.IntegrationSettings;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.WorldLocation;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Pont {@code /warp DLS} (DAT §13.3).
 *
 * <p>La commande canonique reste {@code /dls join}. Deux modes sont proposés :</p>
 * <ul>
 *   <li><strong>destination</strong> (recommandé, actif par défaut) : le fournisseur de warp
 *       téléporte vers un point d'accès DLS ; l'arrivée dans son rayon déclenche l'inscription.
 *       Aucune API tierce n'est requise.</li>
 *   <li><strong>exact-command</strong> : interception stricte de {@code /warp DLS}. Plus
 *       intrusif, donc désactivé par défaut.</li>
 * </ul>
 */
public final class WarpBridgeListener implements Listener {

    private final DlsServices services;

    public WarpBridgeListener(DlsServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    private IntegrationSettings settings() {
        return services.lifecycle().config().integrations();
    }

    /** Mode destination : l'arrivée dans le rayon du point d'accès inscrit le joueur. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        IntegrationSettings integrations = settings();
        if (!IntegrationSettings.WARP_MODE_DESTINATION.equals(integrations.warpBridgeMode())) {
            return;
        }
        // Filtre bon marché : seuls les changements de bloc sont examinés.
        if (event.getTo().getBlockX() == event.getFrom().getBlockX()
                && event.getTo().getBlockY() == event.getFrom().getBlockY()
                && event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }
        if (!services.lifecycle().phase().acceptsRegistration()) {
            return;
        }

        Optional<Location> accessPoint = accessPoint();
        if (accessPoint.isEmpty() || !accessPoint.get().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        double radius = integrations.warpTriggerRadius();
        if (event.getTo().distanceSquared(accessPoint.get()) > radius * radius) {
            return;
        }
        tryJoin(event.getPlayer());
    }

    /** Mode exact-command : seule la commande exactement égale au warp configuré est interceptée. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        IntegrationSettings integrations = settings();
        if (!IntegrationSettings.WARP_MODE_EXACT_COMMAND.equals(integrations.warpBridgeMode())) {
            return;
        }
        String expected = "/warp " + integrations.warpName();
        if (!event.getMessage().trim().equalsIgnoreCase(expected)) {
            return;
        }
        event.setCancelled(true);
        tryJoin(event.getPlayer());
    }

    private void tryJoin(Player player) {
        PlayerId playerId = PlayerId.of(player.getUniqueId());
        if (!player.hasPermission("dls.join")) {
            return;
        }
        services.lifecycle().session().ifPresent(session -> {
            if (session.participant(playerId).isPresent()) {
                return;
            }
            Commands.Outcome outcome = services.registration().join(session, new Commands.Join(playerId));
            services.notifications().message(playerId, outcome.messageKey(), outcome.placeholders());
        });
    }

    private Optional<Location> accessPoint() {
        Optional<WorldLocation> configured = services.lifecycle().config().warpAccessPoint();
        if (configured.isEmpty()) {
            return Optional.empty();
        }
        WorldLocation location = configured.get();
        World world = Bukkit.getWorld(location.worldName());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, location.x(), location.y(), location.z()));
    }

    /** Nom du warp surveillé, exposé au diagnostic. */
    public String watchedWarp() {
        return settings().warpName().toLowerCase(Locale.ROOT);
    }
}
