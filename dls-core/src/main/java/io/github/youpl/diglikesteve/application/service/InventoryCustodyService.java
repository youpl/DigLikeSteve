package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.port.outbound.InventoryCustodyPort;
import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SnapshotRepository;
import io.github.youpl.diglikesteve.core.player.CustodyState;
import io.github.youpl.diglikesteve.core.player.InventorySnapshot;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Garde d'inventaire transactionnelle (DAT §8).
 *
 * <p><strong>Invariant :</strong> l'inventaire n'est jamais vidé avant qu'un snapshot complet
 * ait été persisté <em>et confirmé</em>. Si le joueur se déconnecte avant l'application du kit,
 * son inventaire n'a pas été touché et le snapshot est invalidé. S'il se déconnecte après,
 * le snapshot devient la source de restauration.</p>
 *
 * <p>Un verrou logique par joueur, doublé d'une contrainte d'unicité en base, interdit deux
 * gardes actives pour le même UUID.</p>
 */
public final class InventoryCustodyService {

    private final InventoryCustodyPort custody;
    private final PlayerPort players;
    private final SnapshotRepository repository;
    private final SchedulerPort schedulers;
    private final Clock clock;
    private final LogPort log;

    /** Verrou logique par joueur : une seule saga d'entrée ou de sortie à la fois. */
    private final Set<PlayerId> inFlight = ConcurrentHashMap.newKeySet();

    public InventoryCustodyService(
            InventoryCustodyPort custody,
            PlayerPort players,
            SnapshotRepository repository,
            SchedulerPort schedulers,
            Clock clock,
            LogPort log) {
        this.custody = Objects.requireNonNull(custody, "custody");
        this.players = Objects.requireNonNull(players, "players");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Saga d'entrée (DAT §8.2).
     *
     * @param stillEligible réévalué après la persistance : le joueur doit toujours être en ligne
     *                      et la session doit toujours accepter les inscriptions
     * @return l'identifiant du snapshot si la garde est établie
     */
    public CompletableFuture<Optional<InventorySnapshot>> enter(
            PlayerId playerId,
            SessionId sessionId,
            String worldName,
            BlockPos spawn,
            float yaw,
            Predicate<PlayerId> stillEligible) {

        if (!inFlight.add(playerId)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Optional<InventorySnapshot>> result = new CompletableFuture<>();

        // 1. Capture sur l'EntityScheduler du joueur : rien n'est modifié à ce stade.
        custody.capture(playerId, sessionId)
                // 2. Persistance durable sur le writer unique de la base.
                .thenCompose(snapshot -> persistCaptured(snapshot).thenApply(inserted -> inserted ? snapshot : null))
                .thenCompose(snapshot -> {
                    if (snapshot == null) {
                        log.warn("Garde refusée : une garde active existe déjà", "player", playerId);
                        return CompletableFuture.completedFuture(Optional.<InventorySnapshot>empty());
                    }
                    // 3 et 4. Retour sur le joueur, revalidation de l'éligibilité.
                    if (!players.isOnline(playerId) || !stillEligible.test(playerId)) {
                        invalidate(snapshot);
                        return CompletableFuture.completedFuture(Optional.<InventorySnapshot>empty());
                    }
                    return applyKitThenTeleport(playerId, sessionId, worldName, spawn, yaw, snapshot);
                })
                .whenComplete((outcome, failure) -> {
                    inFlight.remove(playerId);
                    if (failure != null) {
                        log.error("Échec de la saga d'entrée", failure, "player", playerId);
                        result.complete(Optional.empty());
                    } else {
                        result.complete(outcome);
                    }
                });

        return result;
    }

    private CompletableFuture<Boolean> persistCaptured(InventorySnapshot snapshot) {
        CompletableFuture<Boolean> inserted = new CompletableFuture<>();
        schedulers.database(() -> inserted.complete(repository.insertCaptured(snapshot)))
                .exceptionally(failure -> {
                    inserted.completeExceptionally(failure);
                    return null;
                });
        return inserted;
    }

    private CompletableFuture<Optional<InventorySnapshot>> applyKitThenTeleport(
            PlayerId playerId,
            SessionId sessionId,
            String worldName,
            BlockPos spawn,
            float yaw,
            InventorySnapshot snapshot) {

        // 5. Vidage puis kit : c'est le point de non-retour, l'état passe à KIT_APPLIED.
        return custody.clearInventory(playerId)
                .thenCompose(cleared -> {
                    if (!Boolean.TRUE.equals(cleared)) {
                        invalidate(snapshot);
                        return CompletableFuture.completedFuture(Optional.<InventorySnapshot>empty());
                    }
                    return transition(snapshot, CustodyState.CAPTURED, CustodyState.KIT_APPLIED)
                            .thenCompose(moved -> {
                                if (!moved) {
                                    return CompletableFuture.completedFuture(Optional.<InventorySnapshot>empty());
                                }
                                return players.applyKit(playerId, sessionId)
                                        // 6. Téléportation asynchrone puis état ACTIVE.
                                        .thenCompose(applied -> players.teleport(playerId, worldName, spawn, yaw))
                                        .thenCompose(teleported -> transition(snapshot, CustodyState.KIT_APPLIED, CustodyState.ACTIVE))
                                        .thenApply(active -> Optional.of(snapshot.withState(CustodyState.ACTIVE, clock.epochMillis())));
                            });
                });
    }

    /**
     * Saga de sortie (DAT §8.3). Un deuxième appel voit {@code RESTORED} et ne fait rien.
     *
     * @return vrai si cet appel a effectivement rendu l'inventaire
     */
    public CompletableFuture<Boolean> restore(InventorySnapshot snapshot, SessionId sessionId, boolean teleportToExit) {
        PlayerId playerId = snapshot.playerId();
        if (!inFlight.add(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        markRestorePending(snapshot)
                .thenCompose(claimed -> {
                    if (!claimed) {
                        return CompletableFuture.completedFuture(false);
                    }
                    if (!snapshot.isIntact()) {
                        log.error("Snapshot corrompu, restauration abandonnée", null,
                                "snapshot", snapshot.id(), "player", playerId);
                        return CompletableFuture.completedFuture(false);
                    }
                    if (!players.isOnline(playerId)) {
                        // Le joueur est hors ligne : la garde reste en RESTORE_PENDING et sera
                        // traitée au prochain PlayerJoinEvent (DAT §8.4).
                        return CompletableFuture.completedFuture(false);
                    }
                    return players.stripSessionItems(playerId, sessionId)
                            .thenCompose(ignored -> custody.restore(playerId, snapshot))
                            .thenCompose(restored -> {
                                if (!Boolean.TRUE.equals(restored)) {
                                    return CompletableFuture.completedFuture(false);
                                }
                                CompletableFuture<Boolean> exit = teleportToExit
                                        ? players.teleportToExit(playerId)
                                        : CompletableFuture.completedFuture(true);
                                return exit.thenCompose(ignored ->
                                        transition(snapshot, CustodyState.RESTORE_PENDING, CustodyState.RESTORED));
                            });
                })
                .whenComplete((restored, failure) -> {
                    inFlight.remove(playerId);
                    if (failure != null) {
                        log.error("Échec de restauration", failure, "player", playerId, "snapshot", snapshot.id());
                        result.complete(false);
                    } else {
                        result.complete(Boolean.TRUE.equals(restored));
                    }
                });
        return result;
    }

    /**
     * Passe une garde en {@code RESTORE_PENDING} depuis n'importe quel état non terminal.
     * Une garde déjà restaurée renvoie faux : la double restauration est impossible.
     */
    private CompletableFuture<Boolean> markRestorePending(InventorySnapshot snapshot) {
        CompletableFuture<Boolean> claimed = new CompletableFuture<>();
        schedulers.database(() -> claimed.complete(repository.claimForRestore(snapshot.id(), clock.epochMillis())))
                .exceptionally(failure -> {
                    claimed.completeExceptionally(failure);
                    return null;
                });
        return claimed;
    }

    private CompletableFuture<Boolean> transition(InventorySnapshot snapshot, CustodyState from, CustodyState to) {
        CompletableFuture<Boolean> moved = new CompletableFuture<>();
        schedulers.database(() ->
                moved.complete(repository.compareAndSetState(snapshot.id(), from, to, clock.epochMillis()))
        ).exceptionally(failure -> {
            moved.completeExceptionally(failure);
            return null;
        });
        return moved;
    }

    /** Le snapshot n'a jamais servi : l'inventaire du joueur est resté intact. */
    private void invalidate(InventorySnapshot snapshot) {
        schedulers.database(() -> repository.compareAndSetState(
                snapshot.id(), CustodyState.CAPTURED, CustodyState.INVALIDATED, clock.epochMillis()));
    }

    /** Suspend une garde lors d'une déconnexion (DAT §8.4). */
    public CompletableFuture<Void> suspend(InventorySnapshot snapshot) {
        return schedulers.database(() -> repository.compareAndSetState(
                snapshot.id(), CustodyState.ACTIVE, CustodyState.SUSPENDED, clock.epochMillis()));
    }

    /** Réactive une garde lors d'une reconnexion dans les délais. */
    public CompletableFuture<Boolean> resume(InventorySnapshot snapshot) {
        return transition(snapshot, CustodyState.SUSPENDED, CustodyState.ACTIVE);
    }

    public boolean isBusy(PlayerId playerId) {
        return inFlight.contains(playerId);
    }
}
