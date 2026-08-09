package io.github.youpl.diglikesteve.core.game;

import io.github.youpl.diglikesteve.core.shared.MutableClock;
import io.github.youpl.diglikesteve.core.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Machine à états et transitions interdites (DAT §21). */
class SessionPhaseTest {

    @Test
    @DisplayName("le cycle nominal est autorisé de bout en bout")
    void nominalCycle() {
        SessionPhase[] cycle = {
                SessionPhase.IDLE, SessionPhase.PREPARING, SessionPhase.REGISTRATION, SessionPhase.STARTING,
                SessionPhase.RUNNING, SessionPhase.FINISHING, SessionPhase.RESTORING, SessionPhase.CLEANUP,
                SessionPhase.IDLE};
        for (int i = 0; i + 1 < cycle.length; i++) {
            assertTrue(cycle[i].canTransitionTo(cycle[i + 1]), cycle[i] + " -> " + cycle[i + 1]);
        }
    }

    @Test
    @DisplayName("les raccourcis interdits sont refusés")
    void forbiddenShortcuts() {
        assertFalse(SessionPhase.IDLE.canTransitionTo(SessionPhase.RUNNING));
        assertFalse(SessionPhase.RUNNING.canTransitionTo(SessionPhase.REGISTRATION));
        assertFalse(SessionPhase.RUNNING.canTransitionTo(SessionPhase.CLEANUP));
        assertFalse(SessionPhase.FINISHING.canTransitionTo(SessionPhase.RUNNING));
        assertFalse(SessionPhase.CLEANUP.canTransitionTo(SessionPhase.RUNNING));
    }

    @Test
    @DisplayName("toute phase active peut basculer en récupération")
    void recoveryIsAlwaysReachable() {
        for (SessionPhase phase : SessionPhase.values()) {
            if (phase == SessionPhase.IDLE || phase == SessionPhase.CLEANUP || phase == SessionPhase.RECOVERY) {
                continue;
            }
            assertTrue(phase.canTransitionTo(SessionPhase.RECOVERY), phase + " -> RECOVERY");
        }
    }

    @Test
    @DisplayName("seule la phase RUNNING autorise le score")
    void onlyRunningScores() {
        for (SessionPhase phase : SessionPhase.values()) {
            assertEquals(phase == SessionPhase.RUNNING, phase.acceptsScoring(), phase.name());
        }
    }

    @Test
    @DisplayName("une transition invalide lève une exception explicite")
    void invalidTransitionThrows() {
        GameSession session = newSession();
        assertThrows(PhaseTransitionException.class,
                () -> session.transition(SessionPhase.IDLE, SessionPhase.RUNNING));
    }

    @Test
    @DisplayName("un seul appelant remporte une transition concurrente")
    void transitionIsAtomic() throws Exception {
        GameSession session = newSession();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        if (session.transition(SessionPhase.IDLE, SessionPhase.PREPARING)) {
                            winners.incrementAndGet();
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
        assertEquals(1, winners.get());
        assertEquals(SessionPhase.PREPARING, session.phase());
    }

    @Test
    @DisplayName("le règlement final ne peut être réclamé qu'une fois")
    void resultSettlementIsClaimedOnce() throws Exception {
        GameSession session = newSession();
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger claims = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        if (session.claimResultSettlement()) {
                            claims.incrementAndGet();
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
        assertEquals(1, claims.get(), "deux /dls stop ne peuvent pas distribuer deux récompenses");
    }

    private static GameSession newSession() {
        return new GameSession(SessionId.random(), 1L, GameSettings.defaults(), MutableClock.atZero());
    }
}
