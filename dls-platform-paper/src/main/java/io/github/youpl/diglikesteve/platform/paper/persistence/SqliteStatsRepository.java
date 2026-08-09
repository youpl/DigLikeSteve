package io.github.youpl.diglikesteve.platform.paper.persistence;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.StatsRepository;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** Statistiques permanentes facultatives (DAT §12). */
public final class SqliteStatsRepository implements StatsRepository {

    private final SqliteDatabase database;
    private final LogPort log;

    public SqliteStatsRepository(SqliteDatabase database, LogPort log) {
        this.database = Objects.requireNonNull(database, "database");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void recordParticipation(PlayerId playerId, long score, long nowEpochMillis) {
        String sql = """
                INSERT INTO player_stats (player_uuid, games, wins, best_score, updated_at)
                VALUES (?, 1, 0, ?, ?)
                ON CONFLICT (player_uuid)
                DO UPDATE SET games = player_stats.games + 1,
                              best_score = MAX(player_stats.best_score, excluded.best_score),
                              updated_at = excluded.updated_at""";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, Math.max(0L, score));
            statement.setLong(3, nowEpochMillis);
            statement.executeUpdate();
        } catch (SQLException failure) {
            log.error("Enregistrement de statistique impossible", failure, "player", playerId);
        }
    }

    @Override
    public int wins(PlayerId playerId) {
        return (int) readLong(playerId, "wins");
    }

    @Override
    public long bestScore(PlayerId playerId) {
        return readLong(playerId, "best_score");
    }

    @Override
    public int gamesPlayed(PlayerId playerId) {
        return (int) readLong(playerId, "games");
    }

    private long readLong(PlayerId playerId, String column) {
        try (PreparedStatement statement = database.connection()
                .prepareStatement("SELECT " + column + " FROM player_stats WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        } catch (SQLException failure) {
            return 0L;
        }
    }
}
