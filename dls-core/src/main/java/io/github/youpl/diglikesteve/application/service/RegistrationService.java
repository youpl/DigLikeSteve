package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.NotificationPort;
import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SnapshotRepository;
import io.github.youpl.diglikesteve.core.arena.Arena;
import io.github.youpl.diglikesteve.core.arena.SpawnSlot;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.player.InventorySnapshot;
import io.github.youpl.diglikesteve.core.player.Participant;
import io.github.youpl.diglikesteve.core.player.ParticipantStatus;
import io.github.youpl.diglikesteve.core.shared.Deadline;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Inscription, départ, déconnexion et reconnexion (DAT §8.2 et §8.4).
 *
 * <p>La réservation d'un spawn slot précède la garde d'inventaire : si l'arène est pleine,
 * l'inscription est refusée proprement plutôt que de superposer deux joueurs.</p>
 */
public final class RegistrationService {

    private final InventoryCustodyService custody;
    private final RecoveryService recovery;
    private final SnapshotRepository snapshots;
    private final PlayerPort players;
    private final NotificationPort notifications;
    private final SchedulerPort schedulers;
    private final LogPort log;

    public RegistrationService(
            InventoryCustodyService custody,
            RecoveryService recovery,
            SnapshotRepository snapshots,
            PlayerPort players,
            NotificationPort notifications,
            SchedulerPort schedulers,
            LogPort log) {
        this.custody = Objects.requireNonNull(custody, "custody");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.players = Objects.requireNonNull(players, "players");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Inscription transactionnelle. Le résultat est immédiat ; la saga se poursuit en fond. */
    public Commands.Outcome join(GameSession session, Commands.Join command) {
        PlayerId playerId = command.playerId();

        if (!session.phase().acceptsRegistration()) {
            return Commands.Outcome.failure("join.closed");
        }
        if (recovery.isBlocked(playerId)) {
            return Commands.Outcome.failure("join.recovery-pending");
        }
        if (custody.isBusy(playerId)) {
            return Commands.Outcome.failure("join.in-progress");
        }
        if (session.participant(playerId).isPresent()) {
            return Commands.Outcome.failure("join.already");
        }
        Arena arena = session.arena().orElse(null);
        if (arena == null) {
            return Commands.Outcome.failure("join.not-prepared");
        }

        Optional<SpawnSlot> slot = arena.reserveSlot(playerId);
        if (slot.isEmpty()) {
            return Commands.Outcome.failure("join.full");
        }
        if (session.register(playerId).isEmpty()) {
            arena.releaseSlot(playerId);
            return Commands.Outcome.failure("join.already");
        }

        SpawnSlot reserved = slot.get();
        custody.enter(
                        playerId,
                        session.id(),
                        arena.bounds().worldName(),
                        reserved.feet(),
                        reserved.yaw(),
                        id -> session.phase().acceptsRegistration() && players.isOnline(id))
                .thenAccept(snapshot -> onEntryCompleted(session, playerId, snapshot));

        return Commands.Outcome.ok("join.accepted");
    }

    private void onEntryCompleted(GameSession session, PlayerId playerId, Optional<InventorySnapshot> snapshot) {
        if (snapshot.isEmpty()) {
            session.remove(playerId);
            notifications.message(playerId, "join.failed");
            log.warn("Inscription abandonnée", "player", playerId, "session", session.id());
            return;
        }
        session.participant(playerId).ifPresent(participant -> {
            participant.snapshotId(snapshot.get().id());
            participant.compareAndSetStatus(ParticipantStatus.JOINING, ParticipantStatus.ACTIVE);
        });
        // Pendant l'inscription les joueurs sont isolés dans leur slot, sans pouvoir miner.
        players.freeze(playerId, session.phase().acceptsRegistration());
        notifications.showBossBar(playerId);
        notifications.message(playerId, "join.ready");
    }

    /** Départ volontaire : restauration immédiate et libération du slot. */
    public Commands.Outcome leave(GameSession session, Commands.Leave command) {
        PlayerId playerId = command.playerId();
        Participant participant = session.participant(playerId).orElse(null);
        if (participant == null) {
            return Commands.Outcome.failure("leave.not-registered");
        }
        participant.forceStatus(ParticipantStatus.FINISHED);
        restoreParticipant(session, playerId);
        return Commands.Outcome.ok("leave.done");
    }

    /** Déconnexion : suspension pendant le délai de grâce, sans conserver de référence joueur. */
    public void onDisconnected(GameSession session, Commands.PlayerDisconnected command) {
        PlayerId playerId = command.playerId();
        Participant participant = session.participant(playerId).orElse(null);
        if (participant == null) {
            return;
        }
        Deadline grace = Deadline.in(session.clock(), session.settings().reconnectGraceSeconds(), TimeUnit.SECONDS);
        if (!participant.suspend(grace, session.clock())) {
            return;
        }
        notifications.hideBossBar(playerId);
        participant.snapshotId().ifPresent(snapshotId ->
                schedulers.database(() -> snapshots.findById(snapshotId).ifPresent(custody::suspend)));
        log.info("Participant suspendu", "player", playerId, "session", session.id(),
                "graceSeconds", session.settings().reconnectGraceSeconds());
    }

    /**
     * Reconnexion. Avant l'échéance le joueur reprend sa partie et son score ; après, il est
     * disqualifié et restauré avant de pouvoir agir.
     */
    public CompletableFuture<Commands.Outcome> onReconnected(GameSession session, Commands.PlayerReconnected command) {
        PlayerId playerId = command.playerId();
        Participant participant = session.participant(playerId).orElse(null);
        if (participant == null || participant.status() != ParticipantStatus.SUSPENDED) {
            return CompletableFuture.completedFuture(Commands.Outcome.failure("reconnect.none"));
        }

        if (participant.isGraceExpired(session.clock())) {
            participant.forceStatus(ParticipantStatus.DISQUALIFIED);
            restoreParticipant(session, playerId);
            return CompletableFuture.completedFuture(Commands.Outcome.failure("reconnect.too-late"));
        }

        if (!participant.resume()) {
            return CompletableFuture.completedFuture(Commands.Outcome.failure("reconnect.none"));
        }

        CompletableFuture<Commands.Outcome> result = new CompletableFuture<>();
        participant.snapshotId().ifPresentOrElse(
                snapshotId -> schedulers.database(() -> snapshots.findById(snapshotId).ifPresentOrElse(
                        snapshot -> custody.resume(snapshot).thenAccept(resumed -> {
                            returnToSlot(session, playerId);
                            result.complete(Commands.Outcome.ok("reconnect.resumed"));
                        }),
                        () -> result.complete(Commands.Outcome.failure("reconnect.none")))),
                () -> result.complete(Commands.Outcome.failure("reconnect.none")));
        return result;
    }

    private void returnToSlot(GameSession session, PlayerId playerId) {
        session.arena().flatMap(arena -> arena.slotOf(playerId)).ifPresent(slot ->
                players.teleport(playerId, session.arena().orElseThrow().bounds().worldName(),
                        slot.feet(), slot.yaw()));
        notifications.showBossBar(playerId);
    }

    /**
     * Vérifie les délais de grâce échus. Appelée par la tâche périodique globale ; elle ne fait
     * que comparer {@code now >= deadline}, sans compter de ticks.
     */
    public int expireGraces(GameSession session) {
        int expired = 0;
        for (Participant participant : session.participants()) {
            if (participant.status() != ParticipantStatus.SUSPENDED || !participant.isGraceExpired(session.clock())) {
                continue;
            }
            participant.forceStatus(ParticipantStatus.DISQUALIFIED);
            restoreParticipant(session, participant.playerId());
            expired++;
            log.info("Délai de grâce dépassé", "player", participant.playerId(), "session", session.id());
        }
        return expired;
    }

    /** Restaure un participant et libère son slot. Idempotent grâce à la garde en base. */
    public void restoreParticipant(GameSession session, PlayerId playerId) {
        Participant participant = session.participant(playerId).orElse(null);
        if (participant == null) {
            return;
        }
        notifications.hideBossBar(playerId);
        session.arena().ifPresent(arena -> arena.releaseSlot(playerId));
        participant.snapshotId().ifPresent(snapshotId ->
                schedulers.database(() -> snapshots.findById(snapshotId).ifPresent(snapshot ->
                        schedulers.global(() -> custody.restore(snapshot, session.id(), true)
                                .thenAccept(restored -> {
                                    if (restored && participant.status() != ParticipantStatus.DISQUALIFIED) {
                                        participant.forceStatus(ParticipantStatus.RESTORED);
                                    }
                                })))));
    }

    /** Restaure tous les participants encore en garde, en fin de partie. */
    public int restoreAll(GameSession session) {
        int restored = 0;
        for (Participant participant : session.participants()) {
            if (participant.status().isTerminal()) {
                continue;
            }
            if (participant.status() == ParticipantStatus.ACTIVE) {
                participant.forceStatus(ParticipantStatus.FINISHED);
            }
            restoreParticipant(session, participant.playerId());
            restored++;
        }
        return restored;
    }
}
