package io.github.youpl.diglikesteve.platform.paper.persistence;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.SessionRepository;
import io.github.youpl.diglikesteve.core.game.SessionPhase;
import io.github.youpl.diglikesteve.core.player.ParticipantStatus;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/** Métadonnées de session et de participants (DAT §11). */
public final class SqliteSessionRepository implements SessionRepository {

    private final SqliteDatabase database;
    private final LogPort log;

    public SqliteSessionRepository(SqliteDatabase database, LogPort log) {
        this.database = Objects.requireNonNull(database, "database");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void createSession(SessionId sessionId, long seed, String worldName, String boundsDescription,
                              long createdAtEpochMillis) {
        String sql = """
                INSERT INTO game_session (id, seed, world, bounds, phase, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PREPARING', ?, ?)
                ON CONFLICT (id) DO NOTHING""";
        execute(sql, statement -> {
            statement.setString(1, sessionId.toString());
            statement.setLong(2, seed);
            statement.setString(3, worldName);
            statement.setString(4, boundsDescription);
            statement.setLong(5, createdAtEpochMillis);
            statement.setLong(6, createdAtEpochMillis);
        }, "création de session");
    }

    @Override
    public void updatePhase(SessionId sessionId, SessionPhase phase, long nowEpochMillis) {
        execute("UPDATE game_session SET phase = ?, updated_at = ? WHERE id = ?", statement -> {
            statement.setString(1, phase.name());
            statement.setLong(2, nowEpochMillis);
            statement.setString(3, sessionId.toString());
        }, "mise à jour de phase");
    }

    @Override
    public void closeSession(SessionId sessionId, long endedAtEpochMillis) {
        execute("UPDATE game_session SET phase = 'IDLE', ended_at = ?, updated_at = ? WHERE id = ?", statement -> {
            statement.setLong(1, endedAtEpochMillis);
            statement.setLong(2, endedAtEpochMillis);
            statement.setString(3, sessionId.toString());
        }, "clôture de session");
    }

    @Override
    public void upsertParticipant(SessionId sessionId, PlayerId playerId, ParticipantStatus status,
                                  long score, int rank, long nowEpochMillis) {
        String sql = """
                INSERT INTO participant (session_id, player_uuid, status, score, rank, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, player_uuid)
                DO UPDATE SET status = excluded.status, score = excluded.score,
                              rank = excluded.rank, updated_at = excluded.updated_at""";
        execute(sql, statement -> {
            statement.setString(1, sessionId.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, status.name());
            statement.setLong(4, score);
            statement.setInt(5, rank);
            statement.setLong(6, nowEpochMillis);
        }, "mise à jour de participant");
    }

    @Override
    public int abortOpenSessions(long nowEpochMillis) {
        String sql = "UPDATE game_session SET phase = 'IDLE', ended_at = ?, updated_at = ? "
                + "WHERE ended_at IS NULL AND phase <> 'IDLE'";
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, nowEpochMillis);
                statement.setLong(2, nowEpochMillis);
                return statement.executeUpdate();
            }
        });
    }

    private void execute(String sql, StatementBinder binder, String description) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException failure) {
            log.error("Échec SQL : " + description, failure);
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
