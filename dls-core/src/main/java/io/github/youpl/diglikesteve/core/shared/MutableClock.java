package io.github.youpl.diglikesteve.core.shared;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Horloge pilotable, utilisée par les tests déterministes et les bancs de simulation. */
public final class MutableClock implements Clock {

    private final AtomicLong nanos;
    private final AtomicLong millis;

    public MutableClock(long startNanos, long startEpochMillis) {
        this.nanos = new AtomicLong(startNanos);
        this.millis = new AtomicLong(startEpochMillis);
    }

    public static MutableClock atZero() {
        return new MutableClock(0L, 0L);
    }

    public void advance(long amount, TimeUnit unit) {
        nanos.addAndGet(unit.toNanos(amount));
        millis.addAndGet(unit.toMillis(amount));
    }

    @Override
    public long nanoTime() {
        return nanos.get();
    }

    @Override
    public long epochMillis() {
        return millis.get();
    }
}
