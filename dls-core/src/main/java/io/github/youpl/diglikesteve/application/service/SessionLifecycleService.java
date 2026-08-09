package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.NotificationPort;
import io.github.youpl.diglikesteve.application.port.outbound.PlayerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.application.port.outbound.SessionRepository;
import io.github.youpl.diglikesteve.application.port.outbound.StatsRepository;
import io.github.youpl.diglikesteve.configuration.model.DlsConfig;
import io.github.youpl.diglikesteve.core.arena.Arena;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.arena.SpawnSlotSettings;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.game.GameSettings;
import io.github.youpl.diglikesteve.core.game.SessionPhase;
import io.github.youpl.diglikesteve.core.player.Participant;
import io.github.youpl.diglikesteve.core.player.ParticipantStatus;
import io.github.youpl.diglikesteve.core.reward.WinnerDeclared;
import io.github.youpl.diglikesteve.core.score.Leaderboard;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestration du cycle de vie (DAT §5.2).
 *
 * <p>Ce service reste volontairement mince : il enchaîne les phases et délègue chaque règle à un
 * service nommé selon sa responsabilité. Les échéances sont comparées à une horloge murale, si
 * bien qu'une baisse de TPS ne transforme jamais dix minutes en quinze.</p>
 */
public final class SessionLifecycleService {

    /** Période du rafraîchissement d'affichage et des vérifications d'échéance : une seconde. */
    private static final long TICKER_PERIOD_TICKS = 20L;

    private final MinePreparationService preparation;
    private final RegistrationService registration;
    private final ScoringService scoring;
    private final TntAccountingService tnt;
    private final RewardService rewards;
    private final NotificationPort notifications;
    private final PlayerPort players;
    private final SchedulerPort schedulers;
    private final SessionRepository sessions;
    private final StatsRepository stats;
    private final Clock clock;
    private final LogPort log;

    private final AtomicReference<GameSession> current = new AtomicReference<>();
    private final AtomicReference<DlsConfig> config;
    private final AtomicReference<SchedulerPort.TaskHandle> ticker = new AtomicReference<>();
    private final AtomicReference<Leaderboard> finalLeaderboard = new AtomicReference<>(Leaderboard.empty());

    public SessionLifecycleService(
            DlsConfig config,
            MinePreparationService preparation,
            RegistrationService registration,
            ScoringService scoring,
            TntAccountingService tnt,
            RewardService rewards,
            NotificationPort notifications,
            PlayerPort players,
            SchedulerPort schedulers,
            SessionRepository sessions,
            StatsRepository stats,
            Clock clock,
            LogPort log) {
        this.config = new AtomicReference<>(Objects.requireNonNull(config, "config"));
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.tnt = Objects.requireNonNull(tnt, "tnt");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.players = Objects.requireNonNull(players, "players");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    public DlsConfig config() {
        return config.get();
    }

    /** Publication atomique d'une configuration déjà validée (DAT §10.2). */
    public boolean reload(DlsConfig validated) {
        GameSession session = current.get();
        if (session != null && !session.phase().acceptsConfigurationChange()) {
            return false;
        }
        config.set(validated);
        return true;
    }

    public Optional<GameSession> session() {
        return Optional.ofNullable(current.get());
    }

    public SessionPhase phase() {
        GameSession session = current.get();
        return session == null ? SessionPhase.IDLE : session.phase();
    }

    public Leaderboard lastLeaderboard() {
        return finalLeaderboard.get();
    }

    public MinePreparationService preparation() {
        return preparation;
    }

    /**
     * Prépare une partie : validation, plan, remplissage progressif, puis ouverture des
     * inscriptions. L'inscription n'ouvre qu'après préparation complète (DAT §6.3).
     */
    public CompletableFuture<Commands.Outcome> prepareAndStart(Long registrationSeconds, Long gameSeconds) {
        DlsConfig configuration = config.get();
        if (!configuration.isPlayable()) {
            return CompletableFuture.completedFuture(Commands.Outcome.failure("admin.no-arena"));
        }
        GameSession existing = current.get();
        if (existing != null && existing.phase().isActive()) {
            return CompletableFuture.completedFuture(Commands.Outcome.failure("admin.already-running"));
        }

        GameSettings settings = configuration.game();
        if (registrationSeconds != null || gameSeconds != null) {
            settings = settings.withDurations(
                    registrationSeconds != null ? registrationSeconds : settings.registrationSeconds(),
                    gameSeconds != null ? gameSeconds : settings.gameSeconds());
        }

        long seed = clock.epochMillis() ^ System.identityHashCode(this);
        GameSession session = new GameSession(SessionId.random(), seed, settings, clock);
        if (!current.compareAndSet(existing, session)) {
            return CompletableFuture.completedFuture(Commands.Outcome.failure("admin.already-running"));
        }
        if (!session.transition(SessionPhase.IDLE, SessionPhase.PREPARING)) {
            return CompletableFuture.completedFuture(Commands.Outcome.failure("admin.already-running"));
        }

        ArenaBounds bounds = configuration.arena().orElseThrow();
        SpawnSlotSettings slots = new SpawnSlotSettings(
                settings.maxParticipants(),
                configuration.spawnSlots().pocketWidth(),
                configuration.spawnSlots().pocketHeight(),
                configuration.spawnSlots().minDistance(),
                configuration.spawnSlots().hazardSafeRadius(),
                configuration.spawnSlots().maxAttempts());

        schedulers.database(() -> sessions.createSession(session.id(), seed, bounds.worldName(),
                bounds.toString(), session.createdAtEpochMillis()));
        scoring.reset();

        CompletableFuture<Commands.Outcome> outcome = new CompletableFuture<>();
        preparation.prepare(bounds, configuration.palette(), configuration.hazardousPaletteIds(),
                        slots, seed, configuration.budget())
                .whenComplete((arena, failure) -> schedulers.global(() -> {
                    if (failure != null) {
                        log.error("Préparation de la mine échouée", failure, "session", session.id());
                        abort(session, "prepare.failed");
                        outcome.complete(Commands.Outcome.failure("prepare.failed",
                                "error", String.valueOf(failure.getMessage())));
                        return;
                    }
                    openRegistration(session, arena);
                    outcome.complete(Commands.Outcome.ok("prepare.done",
                            "seconds", session.settings().registrationSeconds()));
                }));
        return outcome;
    }

    private void openRegistration(GameSession session, Arena arena) {
        session.arena(arena);
        if (!session.transition(SessionPhase.PREPARING, SessionPhase.REGISTRATION)) {
            return;
        }
        session.openRegistration();
        persistPhase(session);
        notifications.announceRegistrationOpen(session.settings().registrationSeconds());
        startTicker();
        log.info("Inscriptions ouvertes", "session", session.id(),
                "seconds", session.settings().registrationSeconds(), "slots", arena.capacity());
    }

    private void startTicker() {
        SchedulerPort.TaskHandle handle = schedulers.globalRepeating(this::tick, TICKER_PERIOD_TICKS, TICKER_PERIOD_TICKS);
        SchedulerPort.TaskHandle previous = ticker.getAndSet(handle);
        if (previous != null) {
            previous.cancel();
        }
    }

    private void stopTicker() {
        SchedulerPort.TaskHandle handle = ticker.getAndSet(null);
        if (handle != null) {
            handle.cancel();
        }
    }

    /**
     * Battement d'une seconde : il ne fait que rafraîchir l'affichage et comparer
     * {@code now >= deadline}. Aucune logique de durée ne dépend d'un compteur de ticks.
     */
    void tick() {
        GameSession session = current.get();
        if (session == null) {
            stopTicker();
            return;
        }
        switch (session.phase()) {
            case REGISTRATION -> {
                notifications.refreshHud(Leaderboard.empty(), session.registrationDeadline().remainingSeconds(clock));
                if (session.isRegistrationExpired()) {
                    beginGame(session);
                }
            }
            case RUNNING -> {
                registration.expireGraces(session);
                notifications.refreshHud(scoring.leaderboard(session), session.gameDeadline().remainingSeconds(clock));
                if (session.isGameExpired()) {
                    finish(session);
                }
            }
            default -> {
                // Les autres phases ne dépendent pas du battement.
            }
        }
        tnt.purgeExpired();
    }

    private void beginGame(GameSession session) {
        if (!session.transition(SessionPhase.REGISTRATION, SessionPhase.STARTING)) {
            return;
        }
        List<Participant> ready = session.participants().stream()
                .filter(p -> p.status() == ParticipantStatus.ACTIVE)
                .toList();
        if (ready.isEmpty()) {
            notifications.announceCancelled("no-players");
            session.transition(SessionPhase.STARTING, SessionPhase.FINISHING);
            finish(session);
            return;
        }
        if (!session.transition(SessionPhase.STARTING, SessionPhase.RUNNING)) {
            return;
        }
        session.startGameClock();
        persistPhase(session);
        ready.forEach(participant -> players.freeze(participant.playerId(), false));
        notifications.announceGameStarted(session.settings().gameSeconds());
        log.info("Partie démarrée", "session", session.id(), "joueurs", ready.size());
    }

    /** Fin de partie : le score est bloqué immédiatement, puis le classement est figé. */
    public void finish(GameSession session) {
        SessionPhase phase = session.phase();
        if (phase != SessionPhase.FINISHING && !session.transition(phase, SessionPhase.FINISHING)) {
            return;
        }
        persistPhase(session);
        if (!session.claimResultSettlement()) {
            return;
        }

        tnt.settleAll(session);
        Leaderboard leaderboard = scoring.leaderboard(session);
        finalLeaderboard.set(leaderboard);

        List<PlayerId> winners = leaderboard.winners(session.settings().tieStrategy(), session.seed());
        long topScore = leaderboard.isEmpty() ? 0L : leaderboard.entries().getFirst().score();
        notifications.announceGameFinished(leaderboard, winners);
        persistParticipants(session, leaderboard);

        if (!winners.isEmpty()) {
            WinnerDeclared declared = new WinnerDeclared(session.id(), winners, topScore, clock.epochMillis());
            rewards.declare(declared, session.id().toString())
                    .thenCompose(created -> rewards.deliverPending(winners.size() + 8));
        }

        restoreThenCleanup(session);
    }

    private void restoreThenCleanup(GameSession session) {
        if (!session.transition(SessionPhase.FINISHING, SessionPhase.RESTORING)) {
            return;
        }
        persistPhase(session);
        int restored = registration.restoreAll(session);
        log.info("Restauration de fin de partie", "session", session.id(), "joueurs", restored);
        // Laisse aux sagas de restauration le temps de s'achever avant de purger les caches.
        schedulers.globalDelayed(() -> cleanup(session), 40L);
    }

    /** Nettoyage : tâches, TNT, boss bars, tickets de chunks et caches de session. */
    public void cleanup(GameSession session) {
        if (!session.transition(SessionPhase.RESTORING, SessionPhase.CLEANUP)
                && !session.transition(SessionPhase.RECOVERY, SessionPhase.CLEANUP)) {
            return;
        }
        stopTicker();
        tnt.clear();
        notifications.clearAllAudiences();
        preparation.release();
        schedulers.database(() -> sessions.closeSession(session.id(), clock.epochMillis()));
        session.transition(SessionPhase.CLEANUP, SessionPhase.IDLE);
        current.compareAndSet(session, null);
        log.info("Session nettoyée", "session", session.id());
    }

    /**
     * Arrêt manuel, idempotent : deux appels ne peuvent ni restaurer deux fois, ni distribuer
     * deux récompenses (DAT §5.2).
     */
    public Commands.Outcome stop() {
        GameSession session = current.get();
        if (session == null || !session.phase().isActive()) {
            return Commands.Outcome.failure("admin.not-running");
        }
        switch (session.phase()) {
            case PREPARING -> {
                abort(session, "admin.stopped");
                return Commands.Outcome.ok("admin.stopped");
            }
            case REGISTRATION, STARTING, RUNNING -> {
                finish(session);
                return Commands.Outcome.ok("admin.stopped");
            }
            default -> {
                return Commands.Outcome.failure("admin.stop-in-progress");
            }
        }
    }

    /** Erreur critique : bascule en récupération puis nettoie. */
    public void abort(GameSession session, String reason) {
        session.transitionToRecovery();
        persistPhase(session);
        notifications.announceCancelled(reason);
        tnt.settleAll(session);
        registration.restoreAll(session);
        schedulers.globalDelayed(() -> cleanup(session), 40L);
    }

    /** Fermeture du plugin : les inscriptions et le score sont fermés immédiatement. */
    public void onDisable() {
        stopTicker();
        GameSession session = current.getAndSet(null);
        if (session == null) {
            return;
        }
        session.transitionToRecovery();
        tnt.settleAll(session);
        tnt.clear();
        notifications.clearAllAudiences();
        preparation.release();
    }

    private void persistPhase(GameSession session) {
        SessionPhase phase = session.phase();
        schedulers.database(() -> sessions.updatePhase(session.id(), phase, clock.epochMillis()));
    }

    private void persistParticipants(GameSession session, Leaderboard leaderboard) {
        schedulers.database(() -> {
            long now = clock.epochMillis();
            for (Participant participant : session.participants()) {
                sessions.upsertParticipant(session.id(), participant.playerId(), participant.status(),
                        participant.score(), leaderboard.rankOf(participant.playerId()), now);
                // Parties jouées et meilleur score permanent ; les victoires sont comptées avec
                // la création de la récompense, dans la même transaction.
                if (participant.status().isRanked()) {
                    stats.recordParticipation(participant.playerId(), participant.score(), now);
                }
            }
        });
    }
}
