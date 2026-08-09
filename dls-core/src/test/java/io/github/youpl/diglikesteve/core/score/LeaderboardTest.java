package io.github.youpl.diglikesteve.core.score;

import io.github.youpl.diglikesteve.core.game.TieStrategy;
import io.github.youpl.diglikesteve.core.player.Participant;
import io.github.youpl.diglikesteve.core.player.ParticipantStatus;
import io.github.youpl.diglikesteve.core.shared.MutableClock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Classement, rangs partagés et stratégies d'égalité (DAT §21). */
class LeaderboardTest {

    @Test
    @DisplayName("les ex æquo partagent le même rang")
    void tiedPlayersShareRank() {
        MutableClock clock = MutableClock.atZero();
        Participant first = scored(clock, 100L);
        Participant second = scored(clock, 100L);
        Participant third = scored(clock, 40L);

        Leaderboard leaderboard = Leaderboard.of(List.of(first, second, third));
        assertEquals(1, leaderboard.rankOf(first.playerId()));
        assertEquals(1, leaderboard.rankOf(second.playerId()));
        assertEquals(3, leaderboard.rankOf(third.playerId()));
    }

    @Test
    @DisplayName("shared récompense tous les ex æquo")
    void sharedRewardsEveryone() {
        MutableClock clock = MutableClock.atZero();
        Participant first = scored(clock, 90L);
        Participant second = scored(clock, 90L);

        List<PlayerId> winners = Leaderboard.of(List.of(first, second)).winners(TieStrategy.SHARED, 1L);
        assertEquals(2, winners.size());
    }

    @Test
    @DisplayName("first-reached départage par l'instant du meilleur score")
    void firstReachedPicksEarliest() {
        MutableClock clock = MutableClock.atZero();
        Participant early = scored(clock, 90L);
        clock.advance(5L, TimeUnit.SECONDS);
        Participant late = scored(clock, 90L);

        List<PlayerId> winners = Leaderboard.of(List.of(late, early)).winners(TieStrategy.FIRST_REACHED, 1L);
        assertEquals(List.of(early.playerId()), winners);
    }

    @Test
    @DisplayName("random est reproductible depuis la graine et ne désigne qu'un gagnant")
    void randomIsReproducible() {
        MutableClock clock = MutableClock.atZero();
        Participant first = scored(clock, 70L);
        Participant second = scored(clock, 70L);
        Participant third = scored(clock, 70L);
        Leaderboard leaderboard = Leaderboard.of(List.of(first, second, third));

        List<PlayerId> once = leaderboard.winners(TieStrategy.RANDOM, 4242L);
        List<PlayerId> twice = leaderboard.winners(TieStrategy.RANDOM, 4242L);
        assertEquals(1, once.size());
        assertEquals(once, twice);
    }

    @Test
    @DisplayName("les disqualifiés sont exclus du classement")
    void disqualifiedAreExcluded() {
        MutableClock clock = MutableClock.atZero();
        Participant kept = scored(clock, 30L);
        Participant excluded = scored(clock, 999L);
        excluded.forceStatus(ParticipantStatus.DISQUALIFIED);

        Leaderboard leaderboard = Leaderboard.of(List.of(kept, excluded));
        assertEquals(1, leaderboard.entries().size());
        assertEquals(kept.playerId(), leaderboard.entries().getFirst().playerId());
    }

    @Test
    @DisplayName("sans point marqué, aucune récompense n'est due")
    void zeroScoreHasNoWinner() {
        MutableClock clock = MutableClock.atZero();
        Participant idle = scored(clock, 0L);
        assertTrue(Leaderboard.of(List.of(idle)).winners(TieStrategy.SHARED, 1L).isEmpty());
    }

    private static Participant scored(MutableClock clock, long score) {
        Participant participant = new Participant(PlayerId.of(UUID.randomUUID()), clock);
        participant.forceStatus(ParticipantStatus.ACTIVE);
        if (score > 0L) {
            participant.addScore(score, clock);
        }
        return participant;
    }
}
