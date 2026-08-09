package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.shared.PlayerId;

/** Statistiques permanentes facultatives (DAT §12). Le classement de session reste éphémère. */
public interface StatsRepository {

    void recordParticipation(PlayerId playerId, long score, long nowEpochMillis);

    int wins(PlayerId playerId);

    long bestScore(PlayerId playerId);

    int gamesPlayed(PlayerId playerId);
}
