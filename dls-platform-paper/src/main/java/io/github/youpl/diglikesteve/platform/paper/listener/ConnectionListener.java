package io.github.youpl.diglikesteve.platform.paper.listener;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Connexion et déconnexion (DAT §8.4).
 *
 * <p>À la connexion, une restauration en attente est toujours traitée <em>avant</em> toute
 * nouvelle demande d'inscription. Aucune référence {@code Player} n'est conservée : seuls des
 * identifiants sont transmis au domaine.</p>
 */
public final class ConnectionListener implements Listener {

    private final DlsServices services;

    public ConnectionListener(DlsServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerId playerId = PlayerId.of(event.getPlayer().getUniqueId());

        services.recovery().onPlayerJoined(playerId).thenAccept(restored -> {
            if (restored) {
                services.notifications().message(playerId, "recovery.restored");
                return;
            }
            services.lifecycle().session().ifPresent(session ->
                    resumeIfSuspended(session, playerId));
        });

        // Récompenses restées en boîte aux lettres.
        services.rewards().onPlayerJoined(playerId);
    }

    private void resumeIfSuspended(GameSession session, PlayerId playerId) {
        services.registration()
                .onReconnected(session, new Commands.PlayerReconnected(playerId))
                .thenAccept(outcome -> {
                    if (outcome.messageKey() != null && !"reconnect.none".equals(outcome.messageKey())) {
                        services.notifications().message(playerId, outcome.messageKey());
                    }
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerId playerId = PlayerId.of(event.getPlayer().getUniqueId());
        services.lifecycle().session().ifPresent(session ->
                services.registration().onDisconnected(session, new Commands.PlayerDisconnected(playerId)));
    }
}
