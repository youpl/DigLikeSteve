package io.github.youpl.diglikesteve.core.game;

import io.github.youpl.diglikesteve.core.arena.Arena;
import io.github.youpl.diglikesteve.core.player.Participant;
import io.github.youpl.diglikesteve.core.player.ParticipantStatus;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.Deadline;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Racine d'agrégat de la partie (DAT §5.1). Toutes les transitions passent par un
 * {@code compare-and-set} : deux appels concurrents de {@code /dls stop} ne peuvent ni
 * restaurer deux fois, ni distribuer deux récompenses.
 */
public final class GameSession {

    private final SessionId id;
    private final long seed;
    private final Clock clock;
    private final AtomicReference<SessionPhase> phase = new AtomicReference<>(SessionPhase.IDLE);
    private final AtomicReference<GameSettings> settings;
    private final AtomicReference<Arena> arena = new AtomicReference<>();
    private final ConcurrentHashMap<PlayerId, Participant> participants = new ConcurrentHashMap<>();

    private final AtomicReference<Deadline> registrationDeadline = new AtomicReference<>(Deadline.never());
    private final AtomicReference<Deadline> gameDeadline = new AtomicReference<>(Deadline.never());

    /** Garde d'idempotence du règlement final : classement figé et récompense créées une seule fois. */
    private final AtomicBoolean resultSettled = new AtomicBoolean();

    private final long createdAtEpochMillis;

    public GameSession(SessionId id, long seed, GameSettings settings, Clock clock) {
        this.id = Objects.requireNonNull(id, "id");
        this.seed = seed;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = new AtomicReference<>(Objects.requireNonNull(settings, "settings"));
        this.createdAtEpochMillis = clock.epochMillis();
    }

    public SessionId id() {
        return id;
    }

    public long seed() {
        return seed;
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public SessionPhase phase() {
        return phase.get();
    }

    public GameSettings settings() {
        return settings.get();
    }

    public void settings(GameSettings updated) {
        settings.set(Objects.requireNonNull(updated, "updated"));
    }

    public Optional<Arena> arena() {
        return Optional.ofNullable(arena.get());
    }

    public void arena(Arena prepared) {
        arena.set(prepared);
    }

    public Deadline registrationDeadline() {
        return registrationDeadline.get();
    }

    public Deadline gameDeadline() {
        return gameDeadline.get();
    }

    /**
     * Applique une transition si et seulement si la phase courante est {@code expected} et que
     * la transition est autorisée.
     *
     * @return vrai si la transition a été appliquée par cet appel
     */
    public boolean transition(SessionPhase expected, SessionPhase target) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(target, "target");
        if (!expected.canTransitionTo(target)) {
            throw new PhaseTransitionException(expected, target);
        }
        return phase.compareAndSet(expected, target);
    }

    /** Transition depuis la phase courante quelle qu'elle soit, réservée aux erreurs critiques. */
    public boolean transitionToRecovery() {
        SessionPhase current;
        do {
            current = phase.get();
            if (current == SessionPhase.RECOVERY || current == SessionPhase.IDLE) {
                return false;
            }
        } while (!phase.compareAndSet(current, SessionPhase.RECOVERY));
        return true;
    }

    /** Ouvre la fenêtre d'inscription et horodate son échéance. */
    public void openRegistration() {
        registrationDeadline.set(Deadline.in(clock, settings().registrationSeconds(), TimeUnit.SECONDS));
    }

    /** Démarre le chronomètre de jeu sur l'horloge murale, indépendamment des TPS. */
    public void startGameClock() {
        gameDeadline.set(Deadline.in(clock, settings().gameSeconds(), TimeUnit.SECONDS));
    }

    public boolean isRegistrationExpired() {
        return registrationDeadline.get().isExpired(clock);
    }

    public boolean isGameExpired() {
        return gameDeadline.get().isExpired(clock);
    }

    /**
     * Enregistre un participant. Retourne vide si le joueur est déjà inscrit.
     */
    public Optional<Participant> register(PlayerId playerId) {
        Participant created = new Participant(playerId, clock);
        Participant previous = participants.putIfAbsent(playerId, created);
        return previous == null ? Optional.of(created) : Optional.empty();
    }

    public Optional<Participant> participant(PlayerId playerId) {
        return Optional.ofNullable(participants.get(playerId));
    }

    public Collection<Participant> participants() {
        return Collections.unmodifiableCollection(participants.values());
    }

    public void remove(PlayerId playerId) {
        participants.remove(playerId);
        arena().ifPresent(a -> a.releaseSlot(playerId));
    }

    public int activeCount() {
        return (int) participants.values().stream().filter(p -> p.status() == ParticipantStatus.ACTIVE).count();
    }

    public int suspendedCount() {
        return (int) participants.values().stream().filter(p -> p.status() == ParticipantStatus.SUSPENDED).count();
    }

    /**
     * Réserve le règlement final. Le premier appelant obtient {@code true} et est seul autorisé
     * à figer le classement et à créer la récompense (DAT §5.2).
     */
    public boolean claimResultSettlement() {
        return resultSettled.compareAndSet(false, true);
    }

    public boolean isResultSettled() {
        return resultSettled.get();
    }

    public Clock clock() {
        return clock;
    }

    @Override
    public String toString() {
        return "GameSession[" + id + " " + phase.get() + " participants=" + participants.size() + "]";
    }
}
