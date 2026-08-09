package io.github.youpl.diglikesteve.core.score;

import io.github.youpl.diglikesteve.core.shared.PlayerId;

/**
 * Ligne de classement immuable.
 *
 * @param rank        rang à partir de 1, les ex æquo partageant le même rang
 * @param reachedAtNanos instant monotone auquel ce score a été atteint
 */
public record ScoreEntry(PlayerId playerId, long score, int rank, long reachedAtNanos) {
}
