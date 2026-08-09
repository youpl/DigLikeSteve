package io.github.youpl.diglikesteve.core.player;

import io.github.youpl.diglikesteve.core.shared.Deadline;
import io.github.youpl.diglikesteve.core.shared.MutableClock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frontière exacte du délai de reconnexion (DAT §21 : 29,999 s et 30,001 s). */
class ReconnectGraceTest {

    @Test
    @DisplayName("à 29,999 s la grâce n'est pas expirée, à 30,001 s elle l'est")
    void graceBoundary() {
        MutableClock clock = MutableClock.atZero();
        Participant participant = active(clock);
        participant.suspend(Deadline.in(clock, 30L, TimeUnit.SECONDS), clock);

        clock.advance(29_999L, TimeUnit.MILLISECONDS);
        assertFalse(participant.isGraceExpired(clock), "29,999 s doit encore permettre la reprise");

        clock.advance(2L, TimeUnit.MILLISECONDS);
        assertTrue(participant.isGraceExpired(clock), "30,001 s doit disqualifier");
    }

    @Test
    @DisplayName("une reprise dans les délais conserve le score")
    void resumeKeepsScore() {
        MutableClock clock = MutableClock.atZero();
        Participant participant = active(clock);
        participant.addScore(120L, clock);
        participant.suspend(Deadline.in(clock, 30L, TimeUnit.SECONDS), clock);

        clock.advance(10L, TimeUnit.SECONDS);
        assertTrue(participant.resume());
        assertEquals(ParticipantStatus.ACTIVE, participant.status());
        assertEquals(120L, participant.score());
    }

    @Test
    @DisplayName("une déconnexion hors état ACTIVE ne suspend rien")
    void suspendRequiresActive() {
        MutableClock clock = MutableClock.atZero();
        Participant participant = new Participant(PlayerId.of(UUID.randomUUID()), clock);
        assertFalse(participant.suspend(Deadline.in(clock, 30L, TimeUnit.SECONDS), clock));
        assertEquals(ParticipantStatus.JOINING, participant.status());
    }

    @Test
    @DisplayName("l'instant du meilleur score n'avance que sur un nouveau maximum")
    void bestScoreTimestamp() {
        MutableClock clock = MutableClock.atZero();
        Participant participant = active(clock);

        clock.advance(1L, TimeUnit.SECONDS);
        participant.addScore(50L, clock);
        long firstPeak = participant.bestScoreAtNanos();

        clock.advance(1L, TimeUnit.SECONDS);
        // Une pénalité TNT fait baisser le score sans réhorodater le pic.
        participant.addScore(-20L, clock);
        assertEquals(firstPeak, participant.bestScoreAtNanos());
        assertEquals(30L, participant.score());

        clock.advance(1L, TimeUnit.SECONDS);
        participant.addScore(40L, clock);
        assertTrue(participant.bestScoreAtNanos() > firstPeak, "un nouveau maximum réhorodate le pic");
    }

    private static Participant active(MutableClock clock) {
        Participant participant = new Participant(PlayerId.of(UUID.randomUUID()), clock);
        participant.forceStatus(ParticipantStatus.ACTIVE);
        return participant;
    }
}
