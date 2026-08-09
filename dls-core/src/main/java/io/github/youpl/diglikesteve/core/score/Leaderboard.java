package io.github.youpl.diglikesteve.core.score;

import io.github.youpl.diglikesteve.core.game.TieStrategy;
import io.github.youpl.diglikesteve.core.player.Participant;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Classement ponctuel, calculé à la demande sur l'exécuteur CPU privé.
 *
 * <p>Aucun tri global n'est effectué à chaque bloc cassé (DAT §10.4) : le score vit dans un
 * {@code AtomicLong} par participant, et le classement n'est reconstruit que pour l'affichage
 * périodique ou le règlement final.</p>
 */
public final class Leaderboard {

    private final List<ScoreEntry> entries;

    private Leaderboard(List<ScoreEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static Leaderboard empty() {
        return new Leaderboard(List.of());
    }

    /** Ne classe que les participants éligibles ; les disqualifiés sont exclus (DAT §8.4). */
    public static Leaderboard of(Collection<Participant> participants) {
        List<Participant> ranked = new ArrayList<>(participants.size());
        for (Participant participant : participants) {
            if (participant.status().isRanked()) {
                ranked.add(participant);
            }
        }
        ranked.sort(Comparator
                .comparingLong(Participant::score).reversed()
                .thenComparingLong(Participant::bestScoreAtNanos)
                .thenComparing(p -> p.playerId().value()));

        List<ScoreEntry> entries = new ArrayList<>(ranked.size());
        long previousScore = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            Participant participant = ranked.get(i);
            if (participant.score() != previousScore) {
                rank = i + 1;
                previousScore = participant.score();
            }
            entries.add(new ScoreEntry(participant.playerId(), participant.score(), rank,
                    participant.bestScoreAtNanos()));
        }
        return new Leaderboard(entries);
    }

    public List<ScoreEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<ScoreEntry> top(int limit) {
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    /** Rang d'un joueur, ou {@code -1} s'il n'est pas classé. */
    public int rankOf(PlayerId playerId) {
        for (ScoreEntry entry : entries) {
            if (entry.playerId().equals(playerId)) {
                return entry.rank();
            }
        }
        return -1;
    }

    /**
     * Gagnants après application de la stratégie d'égalité (DAT §2).
     *
     * @param seed graine de session, pour rendre {@link TieStrategy#RANDOM} reproductible
     */
    public List<PlayerId> winners(TieStrategy strategy, long seed) {
        Objects.requireNonNull(strategy, "strategy");
        if (entries.isEmpty()) {
            return List.of();
        }
        long best = entries.getFirst().score();
        if (best <= 0L) {
            // Personne n'a marqué : aucune récompense n'est due.
            return List.of();
        }
        List<ScoreEntry> tied = entries.stream().filter(e -> e.score() == best).toList();
        if (tied.size() == 1) {
            return List.of(tied.getFirst().playerId());
        }
        return switch (strategy) {
            case SHARED -> tied.stream().map(ScoreEntry::playerId).toList();
            case FIRST_REACHED -> List.of(tied.stream()
                    .min(Comparator.comparingLong(ScoreEntry::reachedAtNanos))
                    .orElseThrow()
                    .playerId());
            case RANDOM -> {
                List<PlayerId> candidates = tied.stream()
                        .map(ScoreEntry::playerId)
                        .sorted()
                        .toList();
                int picked = new SplittableRandom(seed).nextInt(candidates.size());
                yield List.of(candidates.get(picked));
            }
        };
    }
}
