package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Actions sur un joueur. Toutes s'exécutent sur l'{@code EntityScheduler} du joueur côté
 * adaptateur ; le domaine ne manipule jamais d'objet {@code Player} (DAT §10.1).
 */
public interface PlayerPort {

    boolean isOnline(PlayerId playerId);

    Optional<String> nameOf(PlayerId playerId);

    /** Vide l'inventaire puis applique le kit marqué au PDC de la session (DAT §9). */
    CompletableFuture<Boolean> applyKit(PlayerId playerId, SessionId sessionId);

    /** Supprime tout item DLS et tout contenu acquis pendant l'événement. */
    CompletableFuture<Boolean> stripSessionItems(PlayerId playerId, SessionId sessionId);

    /**
     * Téléportation asynchrone. Le futur ne doit jamais être bloqué sur un thread de région
     * (DAT §10.5) ; les continuations qui modifient le joueur repassent par son scheduler.
     */
    CompletableFuture<Boolean> teleport(PlayerId playerId, String worldName, BlockPos target, float yaw);

    /** Téléportation vers la destination de sortie configurée. */
    CompletableFuture<Boolean> teleportToExit(PlayerId playerId);

    /** Prépare la destination : chunk chargé avant tout départ. */
    CompletableFuture<Void> preloadDestination(String worldName, BlockPos target);

    /** Met le joueur en état « inscrit mais bloqué » pendant la phase d'inscription. */
    CompletableFuture<Boolean> freeze(PlayerId playerId, boolean frozen);

    boolean hasPermission(PlayerId playerId, String permission);
}
