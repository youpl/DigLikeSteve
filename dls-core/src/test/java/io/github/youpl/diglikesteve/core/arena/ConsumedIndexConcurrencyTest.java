package io.github.youpl.diglikesteve.core.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de concurrence du bitset anti-double-score (DAT §21).
 *
 * <p>Une position soumise simultanément par N threads ne doit être payée qu'une fois : c'est ce
 * qui départage une casse manuelle et une explosion visant le même bloc au même tick.</p>
 */
class ConsumedIndexConcurrencyTest {

    @Test
    @DisplayName("une position n'est consommée qu'une seule fois, même sous forte concurrence")
    void singleWinnerPerPosition() throws Exception {
        int threads = 16;
        int positions = 4_096;
        ConsumedIndex index = new ConsumedIndex(positions);
        AtomicInteger[] winners = new AtomicInteger[positions];
        for (int i = 0; i < positions; i++) {
            winners[i] = new AtomicInteger();
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int position = 0; position < positions; position++) {
                            if (index.tryConsume(position)) {
                                winners[position].incrementAndGet();
                            }
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30L, TimeUnit.SECONDS), "les threads doivent terminer");
        } finally {
            pool.shutdownNow();
        }

        for (int position = 0; position < positions; position++) {
            assertEquals(1, winners[position].get(), "position " + position + " payée plusieurs fois");
        }
        assertEquals(positions, index.consumedCount());
        assertEquals(0, index.remaining());
    }

    @Test
    @DisplayName("une position hors bornes n'est jamais payable")
    void outOfBoundsIsNeverPayable() {
        ConsumedIndex index = new ConsumedIndex(64);
        assertFalse(index.tryConsume(-1));
        assertFalse(index.tryConsume(64));
        assertTrue(index.isConsumed(-1), "hors bornes est considéré comme déjà consommé");
    }

    @Test
    @DisplayName("une position marquée non scorable ne rapporte plus rien")
    void unscorableIsNotPayable() {
        ConsumedIndex index = new ConsumedIndex(16);
        index.markUnscorable(3);
        assertFalse(index.tryConsume(3));
        assertTrue(index.isConsumed(3));
    }
}
