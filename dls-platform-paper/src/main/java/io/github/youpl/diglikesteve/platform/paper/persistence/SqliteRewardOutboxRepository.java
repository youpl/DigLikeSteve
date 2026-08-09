package io.github.youpl.diglikesteve.platform.paper.persistence;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.RewardOutboxRepository;
import io.github.youpl.diglikesteve.core.reward.RewardDeliveryState;
import io.github.youpl.diglikesteve.core.reward.RewardOutboxEntry;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox de récompense (DAT §12).
 *
 * <p>{@link #insertIfAbsent} écrit la ligne et incrémente le compteur de victoires dans la même
 * transaction : on ne peut donc pas compter une victoire sans créer la récompense associée.</p>
 */
public final class SqliteRewardOutboxRepository implements RewardOutboxRepository {

    private final SqliteDatabase database;
    private final LogPort log;

    public SqliteRewardOutboxRepository(SqliteDatabase database, LogPort log) {
        this.database = Objects.requireNonNull(database, "database");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean insertIfAbsent(RewardOutboxEntry entry, boolean incrementWins) {
        return database.inTransaction(connection -> {
            String insert = """
                    INSERT INTO reward_outbox
                        (id, session_id, player_uuid, idempotency_key, strategy, payload, state,
                         attempts, created_at, updated_at, last_error)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (idempotency_key) DO NOTHING""";
            int inserted;
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, entry.id().toString());
                statement.setString(2, entry.sessionId().toString());
                statement.setString(3, entry.playerId().toString());
                statement.setString(4, entry.idempotencyKey());
                statement.setString(5, entry.strategy());
                statement.setString(6, entry.payload());
                statement.setString(7, entry.state().name());
                statement.setInt(8, entry.attempts());
                statement.setLong(9, entry.createdAtEpochMillis());
                statement.setLong(10, entry.updatedAtEpochMillis());
                statement.setString(11, entry.lastError());
                inserted = statement.executeUpdate();
            }
            if (inserted == 1 && incrementWins) {
                String wins = """
                        INSERT INTO player_stats (player_uuid, games, wins, best_score, updated_at)
                        VALUES (?, 0, 1, 0, ?)
                        ON CONFLICT (player_uuid)
                        DO UPDATE SET wins = player_stats.wins + 1, updated_at = excluded.updated_at""";
                try (PreparedStatement statement = connection.prepareStatement(wins)) {
                    statement.setString(1, entry.playerId().toString());
                    statement.setLong(2, entry.updatedAtEpochMillis());
                    statement.executeUpdate();
                }
            }
            return inserted == 1;
        });
    }

    @Override
    public List<RewardOutboxEntry> findByState(RewardDeliveryState state, int limit) {
        String sql = "SELECT * FROM reward_outbox WHERE state = ? ORDER BY created_at LIMIT ?";
        List<RewardOutboxEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, state.name());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(map(rows));
                }
            }
        } catch (SQLException failure) {
            log.error("Lecture de l'outbox impossible", failure, "state", state);
        }
        return entries;
    }

    @Override
    public List<RewardOutboxEntry> findPendingForPlayer(PlayerId playerId) {
        String sql = "SELECT * FROM reward_outbox WHERE player_uuid = ? AND state IN ('PENDING','MAILBOX','FAILED')";
        List<RewardOutboxEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(map(rows));
                }
            }
        } catch (SQLException failure) {
            log.error("Lecture des récompenses en attente impossible", failure, "player", playerId);
        }
        return entries;
    }

    @Override
    public Optional<RewardOutboxEntry> findById(UUID id) {
        try (PreparedStatement statement = database.connection()
                .prepareStatement("SELECT * FROM reward_outbox WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            log.error("Lecture d'une récompense impossible", failure, "entry", id);
            return Optional.empty();
        }
    }

    @Override
    public void updateState(UUID id, RewardDeliveryState state, int attempts, String lastError, long nowEpochMillis) {
        String sql = "UPDATE reward_outbox SET state = ?, attempts = ?, last_error = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, state.name());
            statement.setInt(2, attempts);
            statement.setString(3, lastError);
            statement.setLong(4, nowEpochMillis);
            statement.setString(5, id.toString());
            statement.executeUpdate();
        } catch (SQLException failure) {
            log.error("Mise à jour de l'outbox impossible", failure, "entry", id);
        }
    }

    @Override
    public int countByState(RewardDeliveryState state) {
        try (PreparedStatement statement = database.connection()
                .prepareStatement("SELECT COUNT(*) FROM reward_outbox WHERE state = ?")) {
            statement.setString(1, state.name());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException failure) {
            return 0;
        }
    }

    private static RewardOutboxEntry map(ResultSet rows) throws SQLException {
        return new RewardOutboxEntry(
                UUID.fromString(rows.getString("id")),
                SessionId.parse(rows.getString("session_id")),
                PlayerId.parse(rows.getString("player_uuid")),
                rows.getString("idempotency_key"),
                rows.getString("strategy"),
                rows.getString("payload"),
                RewardDeliveryState.valueOf(rows.getString("state")),
                rows.getInt("attempts"),
                rows.getLong("created_at"),
                rows.getLong("updated_at"),
                rows.getString("last_error"));
    }
}
