package io.github.youpl.diglikesteve.core.tnt;

import io.github.youpl.diglikesteve.core.shared.PlayerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arrondis de demi-cœurs, plafonnement et idempotence du règlement (DAT §21). */
class TntPenaltyCalculatorTest {

    @Test
    @DisplayName("l'exemple du DAT : 100 points bruts et 3 demi-cœurs donnent 85 nets")
    void datExample() {
        TntSettlement settlement = TntPenaltyCalculator.settle(100L, 3.0d, 0.05d, true);
        assertEquals(100L, settlement.grossPoints());
        assertEquals(3, settlement.halfHearts());
        assertEquals(0.15d, settlement.rate(), 1.0e-9d);
        assertEquals(15L, settlement.penalty());
        assertEquals(85L, settlement.netPoints());
    }

    @Test
    @DisplayName("les demi-cœurs sont arrondis au supérieur, epsilon compris")
    void halfHeartsRounding() {
        assertEquals(0, TntPenaltyCalculator.halfHearts(0.0d));
        assertEquals(1, TntPenaltyCalculator.halfHearts(0.4d));
        assertEquals(3, TntPenaltyCalculator.halfHearts(3.0d));
        // Sans epsilon, cette valeur donnerait 4 demi-cœurs.
        assertEquals(3, TntPenaltyCalculator.halfHearts(3.0000000001d));
        assertEquals(4, TntPenaltyCalculator.halfHearts(3.2d));
    }

    @Test
    @DisplayName("le taux est plafonné à 100 % et la pénalité au gain de la TNT")
    void rateIsCapped() {
        TntSettlement settlement = TntPenaltyCalculator.settle(50L, 40.0d, 0.05d, true);
        assertEquals(1.0d, settlement.rate(), 1.0e-9d);
        assertEquals(50L, settlement.penalty());
        assertEquals(0L, settlement.netPoints());
        assertTrue(settlement.netPoints() >= 0L, "la pénalité ne peut pas retirer de points acquis avant");
    }

    @Test
    @DisplayName("sans dégât, le gain brut est intégralement crédité")
    void noDamageNoPenalty() {
        TntSettlement settlement = TntPenaltyCalculator.settle(80L, 0.0d, 0.05d, true);
        assertEquals(0L, settlement.penalty());
        assertEquals(80L, settlement.netPoints());
    }

    @Test
    @DisplayName("une TNT qui ne casse rien de scorable ne rapporte ni ne retire rien")
    void tntItselfIsWorthZero() {
        TntSettlement settlement = TntPenaltyCalculator.settle(0L, 6.0d, 0.05d, true);
        assertEquals(0L, settlement.grossPoints());
        assertEquals(0L, settlement.netPoints());
        assertEquals(0L, settlement.penalty());
    }

    @Test
    @DisplayName("un compte réglé plusieurs fois ne modifie le score qu'une fois")
    void settlementIsIdempotent() throws Exception {
        ExplosionAccount account = new ExplosionAccount(UUID.randomUUID(),
                PlayerId.of(UUID.randomUUID()), 0L);
        account.addBlockPoints(120L);
        account.addOwnerDamage(2.0d);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger settled = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        Optional<TntSettlement> result = account.settle(0.05d, true);
                        if (result.isPresent()) {
                            settled.incrementAndGet();
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10L, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, settled.get(), "un seul appel doit remporter le règlement");
        assertTrue(account.isSettled());
    }
}
