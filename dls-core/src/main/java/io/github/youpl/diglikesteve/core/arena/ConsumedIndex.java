package io.github.youpl.diglikesteve.core.arena;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Bitset atomique des positions déjà payées (DAT §6.5).
 *
 * <p>Une casse manuelle et une explosion qui visent la même position dans le même tick sont
 * départagées par un {@code compare-and-set} : une seule des deux obtient le bit et donc les
 * points. Replacer un bloc puis le recasser ne peut jamais repayer une position.</p>
 */
public final class ConsumedIndex {

    private final int volume;
    private final AtomicLongArray words;
    private final AtomicInteger consumedCount = new AtomicInteger();

    public ConsumedIndex(int volume) {
        if (volume < 0) {
            throw new IllegalArgumentException("Volume négatif");
        }
        this.volume = volume;
        this.words = new AtomicLongArray((volume + 63) >>> 6);
    }

    public int volume() {
        return volume;
    }

    /**
     * Tente de consommer une position.
     *
     * @return {@code true} si l'appelant vient de passer le bit de 0 à 1 et peut être crédité
     */
    public boolean tryConsume(int index) {
        if (index < 0 || index >= volume) {
            return false;
        }
        int word = index >>> 6;
        long mask = 1L << (index & 63);
        long current;
        do {
            current = words.get(word);
            if ((current & mask) != 0L) {
                return false;
            }
        } while (!words.compareAndSet(word, current, current | mask));
        consumedCount.incrementAndGet();
        return true;
    }

    public boolean isConsumed(int index) {
        if (index < 0 || index >= volume) {
            return true;
        }
        return (words.get(index >>> 6) & (1L << (index & 63))) != 0L;
    }

    /** Marque une position comme non payable sans la créditer (poche de spawn, air). */
    public void markUnscorable(int index) {
        tryConsume(index);
    }

    public int consumedCount() {
        return consumedCount.get();
    }

    public int remaining() {
        return volume - consumedCount.get();
    }
}
