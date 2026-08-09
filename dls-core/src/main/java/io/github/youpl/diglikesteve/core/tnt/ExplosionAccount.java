package io.github.youpl.diglikesteve.core.tnt;

import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Compte temporaire ouvert par une explosion (DAT §5.1 et §7.3).
 *
 * <p>L'événement de blocs et l'événement de dégâts arrivent dans un ordre non garanti : le
 * compte les accumule séparément, puis un unique règlement différé les réunit. Le drapeau
 * {@code settled} rend ce règlement idempotent — une TNT réglée deux fois ne modifie le score
 * qu'une fois.</p>
 */
public final class ExplosionAccount {

    private final UUID tntId;
    private final PlayerId owner;
    private final long createdAtNanos;

    private final AtomicLong grossPoints = new AtomicLong();
    private final DoubleAdder ownerDamage = new DoubleAdder();
    private final AtomicBoolean settled = new AtomicBoolean();

    public ExplosionAccount(UUID tntId, PlayerId owner, long createdAtNanos) {
        this.tntId = Objects.requireNonNull(tntId, "tntId");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.createdAtNanos = createdAtNanos;
    }

    public UUID tntId() {
        return tntId;
    }

    public PlayerId owner() {
        return owner;
    }

    public long createdAtNanos() {
        return createdAtNanos;
    }

    public long grossPoints() {
        return grossPoints.get();
    }

    public double ownerDamage() {
        return ownerDamage.sum();
    }

    public boolean isSettled() {
        return settled.get();
    }

    /** Additionne les points des positions que cette explosion a effectivement consommées. */
    public void addBlockPoints(long points) {
        if (points > 0L) {
            grossPoints.addAndGet(points);
        }
    }

    /**
     * Additionne les dégâts finaux subis par le propriétaire.
     *
     * <p>Seul le propriétaire est concerné : un adversaire touché ne peut donc pas faire perdre
     * de points à la victime (DAT §2).</p>
     */
    public void addOwnerDamage(double finalDamage) {
        if (finalDamage > 0.0d) {
            ownerDamage.add(finalDamage);
        }
    }

    /**
     * Règle le compte une seule et unique fois.
     *
     * @return le règlement si cet appel a remporté le drapeau, vide sinon
     */
    public Optional<TntSettlement> settle(double ratePerHalfHeart, boolean capPenaltyToGross) {
        if (!settled.compareAndSet(false, true)) {
            return Optional.empty();
        }
        return Optional.of(TntPenaltyCalculator.settle(
                grossPoints.get(), ownerDamage.sum(), ratePerHalfHeart, capPenaltyToGross));
    }

    @Override
    public String toString() {
        return "ExplosionAccount[tnt=" + tntId + " owner=" + owner + " gross=" + grossPoints.get()
                + " damage=" + ownerDamage.sum() + " settled=" + settled.get() + "]";
    }
}
