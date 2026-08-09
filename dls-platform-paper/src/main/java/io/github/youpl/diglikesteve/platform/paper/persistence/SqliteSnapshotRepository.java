package io.github.youpl.diglikesteve.platform.paper.persistence;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.SnapshotRepository;
import io.github.youpl.diglikesteve.core.player.CustodyState;
import io.github.youpl.diglikesteve.core.player.InventorySnapshot;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance des gardes d'inventaire (DAT §11).
 *
 * <p>Toutes les méthodes sont appelées depuis le writer unique {@code dls-db-1}. L'index unique
 * partiel {@code ux_snapshot_active} rend l'insertion d'une deuxième garde active impossible.</p>
 */
public final class SqliteSnapshotRepository implements SnapshotRepository {

    private static final String NON_TERMINAL = "('CAPTURED','KIT_APPLIED','ACTIVE','SUSPENDED','RESTORE_PENDING')";
    private static final String REQUIRES_RECOVERY = "('KIT_APPLIED','ACTIVE','SUSPENDED','RESTORE_PENDING')";

    private final SqliteDatabase database;
    private final LogPort log;

    public SqliteSnapshotRepository(SqliteDatabase database, LogPort log) {
        this.database = Objects.requireNonNull(database, "database");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean insertCaptured(InventorySnapshot snapshot) {
        String sql = """
                INSERT INTO inventory_snapshot
                    (id, player_uuid, session_id, payload, checksum, format_version, state, captured_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, snapshot.id().toString());
            statement.setString(2, snapshot.playerId().toString());
            statement.setString(3, snapshot.sessionId().toString());
            statement.setBytes(4, snapshot.payload());
            statement.setLong(5, snapshot.checksum());
            statement.setInt(6, snapshot.formatVersion());
            statement.setString(7, snapshot.state().name());
            statement.setLong(8, snapshot.capturedAtEpochMillis());
            statement.setLong(9, snapshot.updatedAtEpochMillis());
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            // Violation d'unicité : une garde active existe déjà pour ce joueur.
            log.warn("Insertion de snapshot refusée", "player", snapshot.playerId(),
                    "error", failure.getMessage());
            return false;
        }
    }

    @Override
    public boolean compareAndSetState(UUID snapshotId, CustodyState expected, CustodyState target, long nowEpochMillis) {
        String sql = "UPDATE inventory_snapshot SET state = ?, updated_at = ? WHERE id = ? AND state = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, target.name());
            statement.setLong(2, nowEpochMillis);
            statement.setString(3, snapshotId.toString());
            statement.setString(4, expected.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            log.error("Transition d'état de garde impossible", failure, "snapshot", snapshotId);
            return false;
        }
    }

    @Override
    public boolean claimForRestore(UUID snapshotId, long nowEpochMillis) {
        String sql = "UPDATE inventory_snapshot SET state = 'RESTORE_PENDING', updated_at = ? "
                + "WHERE id = ? AND state IN " + NON_TERMINAL;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, nowEpochMillis);
            statement.setString(2, snapshotId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            log.error("Réclamation de restauration impossible", failure, "snapshot", snapshotId);
            return false;
        }
    }

    @Override
    public Optional<InventorySnapshot> findById(UUID snapshotId) {
        String sql = "SELECT * FROM inventory_snapshot WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            log.error("Lecture de snapshot impossible", failure, "snapshot", snapshotId);
            return Optional.empty();
        }
    }

    @Override
    public Optional<InventorySnapshot> findActiveByPlayer(PlayerId playerId) {
        String sql = "SELECT * FROM inventory_snapshot WHERE player_uuid = ? AND state IN " + NON_TERMINAL
                + " ORDER BY captured_at DESC LIMIT 1";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            log.error("Lecture de garde active impossible", failure, "player", playerId);
            return Optional.empty();
        }
    }

    @Override
    public List<InventorySnapshot> findRequiringRecovery() {
        String sql = "SELECT * FROM inventory_snapshot WHERE state IN " + REQUIRES_RECOVERY + " ORDER BY captured_at";
        List<InventorySnapshot> snapshots = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                snapshots.add(map(rows));
            }
        } catch (SQLException failure) {
            log.error("Lecture des gardes à récupérer impossible", failure);
        }
        return snapshots;
    }

    @Override
    public int markAllPendingRestore(long nowEpochMillis) {
        String sql = "UPDATE inventory_snapshot SET state = 'RESTORE_PENDING', updated_at = ? "
                + "WHERE state IN ('KIT_APPLIED','ACTIVE','SUSPENDED')";
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, nowEpochMillis);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public int purgeRestoredOlderThan(long epochMillis, int batchSize) {
        String sql = "DELETE FROM inventory_snapshot WHERE id IN ("
                + "SELECT id FROM inventory_snapshot WHERE state IN ('RESTORED','INVALIDATED') "
                + "AND updated_at < ? LIMIT ?)";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, epochMillis);
            statement.setInt(2, batchSize);
            return statement.executeUpdate();
        } catch (SQLException failure) {
            log.error("Purge des snapshots impossible", failure);
            return 0;
        }
    }

    @Override
    public int countRequiringRecovery() {
        String sql = "SELECT COUNT(*) FROM inventory_snapshot WHERE state IN " + REQUIRES_RECOVERY;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException failure) {
            return 0;
        }
    }

    private static InventorySnapshot map(ResultSet rows) throws SQLException {
        return new InventorySnapshot(
                UUID.fromString(rows.getString("id")),
                PlayerId.parse(rows.getString("player_uuid")),
                SessionId.parse(rows.getString("session_id")),
                rows.getBytes("payload"),
                rows.getLong("checksum"),
                rows.getInt("format_version"),
                CustodyState.valueOf(rows.getString("state")),
                rows.getLong("captured_at"),
                rows.getLong("updated_at"));
    }

    /** Utilisé par le bootstrap pour partager la connexion avec les autres dépôts. */
    public Connection connection() {
        return database.connection();
    }
}
