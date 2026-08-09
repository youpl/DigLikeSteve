package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SessionRepository;
import io.github.youpl.diglikesteve.application.port.outbound.SnapshotRepository;
import io.github.youpl.diglikesteve.core.player.InventorySnapshot;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reprise après arrêt anormal (DAT §8.5).
 *
 * <p>Au démarrage, toute garde non terminale passe en restauration obligatoire. Tant qu'elle
 * n'est pas terminée, le joueur concerné ne peut rejoindre aucune nouvelle session. Aucun
 * snapshot n'est supprimé tant qu'il n'est pas confirmé restauré.</p>
 */
public final class RecoveryService {

    private final SnapshotRepository snapshots;
    private final SessionRepository sessions;
    private final InventoryCustodyService custody;
    private final PlayerPort players;
    private final SchedulerPort schedulers;
    private final Clock clock;
    private final LogPort log;

    /** Joueurs bloqués tant que leur restauration n'est pas confirmée. */
    private final Set<PlayerId> blocked = ConcurrentHashMap.newKeySet();

    public RecoveryService(
            SnapshotRepository snapshots,
            SessionRepository sessions,
            InventoryCustodyService custody,
            PlayerPort players,
            SchedulerPort schedulers,
            Clock clock,
            LogPort log) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.custody = Objects.requireNonNull(custody, "custody");
        this.players = Objects.requireNonNull(players, "players");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Balayage de démarrage. Un redémarrage annule la session active : aucune reprise de partie
     * n'est tentée, seuls les inventaires sont sauvés.
     */
    public CompletableFuture<Integer> recoverOnStartup() {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        schedulers.database(() -> {
            long now = clock.epochMillis();
            int aborted = sessions.abortOpenSessions(now);
            int pending = snapshots.markAllPendingRestore(now);
            List<InventorySnapshot> toRestore = snapshots.findRequiringRecovery();
            toRestore.forEach(snapshot -> blocked.add(snapshot.playerId()));
            log.info("Récupération au démarrage", "sessionsAnnulées", aborted,
                    "gardesEnAttente", pending, "àRestaurer", toRestore.size());

            schedulers.global(() -> {
                toRestore.forEach(this::restoreIfOnline);
                result.complete(toRestore.size());
            });
        }).exceptionally(failure -> {
            log.error("Échec de la récupération au démarrage", failure);
            result.complete(0);
            return null;
        });
        return result;
    }

    /**
     * À la connexion d'un joueur : une restauration en attente est toujours traitée avant
     * toute nouvelle demande d'inscription (DAT §8.4).
     */
    public CompletableFuture<Boolean> onPlayerJoined(PlayerId playerId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        schedulers.database(() -> snapshots.findActiveByPlayer(playerId).ifPresentOrElse(
                snapshot -> {
                    if (!snapshot.state().requiresRecovery()) {
                        result.complete(false);
                        return;
                    }
                    blocked.add(playerId);
                    schedulers.global(() -> custody.restore(snapshot, snapshot.sessionId(), true)
                            .thenAccept(restored -> {
                                if (restored) {
                                    blocked.remove(playerId);
                                }
                                result.complete(restored);
                            }));
                },
                () -> {
                    blocked.remove(playerId);
                    result.complete(false);
                }));
        return result;
    }

    private void restoreIfOnline(InventorySnapshot snapshot) {
        if (!players.isOnline(snapshot.playerId())) {
            // La restauration s'exécutera au prochain PlayerJoinEvent.
            return;
        }
        custody.restore(snapshot, snapshot.sessionId(), true)
                .thenAccept(restored -> {
                    if (restored) {
                        blocked.remove(snapshot.playerId());
                    }
                });
    }

    /** Un joueur avec une restauration en attente ne peut pas rejoindre une nouvelle session. */
    public boolean isBlocked(PlayerId playerId) {
        return blocked.contains(playerId);
    }

    public int blockedCount() {
        return blocked.size();
    }

    public CompletableFuture<List<InventorySnapshot>> list() {
        CompletableFuture<List<InventorySnapshot>> result = new CompletableFuture<>();
        schedulers.database(() -> result.complete(snapshots.findRequiringRecovery()));
        return result;
    }

    /** Force la restauration de toutes les gardes en attente, commande d'administration. */
    public CompletableFuture<Integer> restoreAll() {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        schedulers.database(() -> {
            List<InventorySnapshot> toRestore = snapshots.findRequiringRecovery();
            schedulers.global(() -> {
                toRestore.forEach(this::restoreIfOnline);
                result.complete(toRestore.size());
            });
        });
        return result;
    }

    /**
     * Fermeture propre (DAT §8.5) : les gardes restantes passent en {@code RESTORE_PENDING} dans
     * une transaction, puis les joueurs encore en ligne sont restaurés immédiatement.
     */
    public void onDisable() {
        long now = clock.epochMillis();
        int pending = snapshots.markAllPendingRestore(now);
        List<InventorySnapshot> toRestore = snapshots.findRequiringRecovery();
        log.info("Arrêt : gardes à restaurer", "pending", pending, "restants", toRestore.size());
        for (InventorySnapshot snapshot : toRestore) {
            if (players.isOnline(snapshot.playerId())) {
                custody.restore(snapshot, snapshot.sessionId(), false);
            }
        }
    }
}
