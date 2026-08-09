package io.github.youpl.diglikesteve.core.shared;

import java.util.concurrent.TimeUnit;

/**
 * Échéance à double représentation : {@code nanoDeadline} pour la comparaison monotone en
 * mémoire, {@code epochMillis} pour la persistance et la reprise après incident (DAT §5.3).
 */
public record Deadline(long nanoDeadline, long epochMillis) {

    public static Deadline in(Clock clock, long amount, TimeUnit unit) {
        return new Deadline(clock.nanoTime() + unit.toNanos(amount), clock.epochMillis() + unit.toMillis(amount));
    }

    public static Deadline never() {
        return new Deadline(Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public boolean isExpired(Clock clock) {
        return clock.nanoTime() - nanoDeadline >= 0L;
    }

    /** Millisecondes restantes, jamais négatives. */
    public long remainingMillis(Clock clock) {
        long remaining = nanoDeadline - clock.nanoTime();
        return remaining <= 0L ? 0L : TimeUnit.NANOSECONDS.toMillis(remaining);
    }

    public long remainingSeconds(Clock clock) {
        return (remainingMillis(clock) + 999L) / 1000L;
    }
}
