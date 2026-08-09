package io.github.youpl.diglikesteve.core.player;

import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.Deadline;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Données métier d'un joueur inscrit (DAT §5.1). Aucune référence à un objet de plateforme
 * n'est conservée : seuls des identifiants et des primitives traversent les frontières
 * de thread (DAT §10.1).
 */
public final class Participant {

    private final PlayerId playerId;
    private final AtomicReference<ParticipantStatus> status = new AtomicReference<>(ParticipantStatus.JOINING);
    private final AtomicLong score = new AtomicLong();
    private final AtomicLong bestScore = new AtomicLong();
    private final AtomicReference<UUID> snapshotId = new AtomicReference<>();
    private final AtomicReference<Deadline> graceDeadline = new AtomicReference<>();

    /** Instant monotone auquel le meilleur score courant a été atteint pour la première fois. */
    private volatile long bestScoreAtNanos;
    private volatile long disconnectedAtNanos = -1L;
    private volatile long joinedAtNanos;

    public Participant(PlayerId playerId, Clock clock) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.joinedAtNanos = clock.nanoTime();
        this.bestScoreAtNanos = this.joinedAtNanos;
    }

    public PlayerId playerId() {
        return playerId;
    }

    public ParticipantStatus status() {
        return status.get();
    }

    public boolean compareAndSetStatus(ParticipantStatus expected, ParticipantStatus target) {
        return status.compareAndSet(expected, target);
    }

    public void forceStatus(ParticipantStatus target) {
        status.set(Objects.requireNonNull(target, "target"));
    }

    public long score() {
        return score.get();
    }

    public long joinedAtNanos() {
        return joinedAtNanos;
    }

    public long bestScoreAtNanos() {
        return bestScoreAtNanos;
    }

    /**
     * Ajoute des points, en horodatant le moment où un nouveau maximum est atteint : c'est ce
     * qui permet à la stratégie {@code first-reached} de départager deux ex æquo.
     *
     * @param delta variation, négative pour une pénalité TNT
     * @return le score après application
     */
    public long addScore(long delta, Clock clock) {
        long updated = score.addAndGet(delta);
        if (delta > 0L) {
            long previousBest;
            do {
                previousBest = bestScore.get();
                if (updated <= previousBest) {
                    return updated;
                }
            } while (!bestScore.compareAndSet(previousBest, updated));
            bestScoreAtNanos = clock.nanoTime();
        }
        return updated;
    }

    public Optional<UUID> snapshotId() {
        return Optional.ofNullable(snapshotId.get());
    }

    public void snapshotId(UUID id) {
        snapshotId.set(id);
    }

    public Optional<Deadline> graceDeadline() {
        return Optional.ofNullable(graceDeadline.get());
    }

    /** Passe en suspension. Retourne faux si le joueur n'était pas actif. */
    public boolean suspend(Deadline deadline, Clock clock) {
        if (!status.compareAndSet(ParticipantStatus.ACTIVE, ParticipantStatus.SUSPENDED)) {
            return false;
        }
        graceDeadline.set(deadline);
        disconnectedAtNanos = clock.nanoTime();
        return true;
    }

    /** Reprend la partie après une reconnexion dans les délais. */
    public boolean resume() {
        if (!status.compareAndSet(ParticipantStatus.SUSPENDED, ParticipantStatus.ACTIVE)) {
            return false;
        }
        graceDeadline.set(null);
        disconnectedAtNanos = -1L;
        return true;
    }

    public boolean isGraceExpired(Clock clock) {
        Deadline deadline = graceDeadline.get();
        return deadline != null && deadline.isExpired(clock);
    }

    public long disconnectedAtNanos() {
        return disconnectedAtNanos;
    }

    @Override
    public String toString() {
        return "Participant[" + playerId + " " + status.get() + " score=" + score.get() + "]";
    }
}
