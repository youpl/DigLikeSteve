package io.github.youpl.diglikesteve.platform.paper.persistence;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/**
 * Base SQLite du plugin (DAT §11).
 *
 * <p>Une seule connexion est ouverte et n'est utilisée que depuis le writer unique
 * {@code dls-db-1} : sur SQLite, multiplier les écrivains n'apporte rien et augmente les
 * contentions. Les migrations sont appliquées avant d'accepter la moindre commande.</p>
 */
public final class SqliteDatabase implements AutoCloseable {

    private static final List<String> MIGRATIONS = List.of("V1__init.sql");

    private final Connection connection;
    private final LogPort log;

    private SqliteDatabase(Connection connection, LogPort log) {
        this.connection = connection;
        this.log = log;
    }

    public static SqliteDatabase open(Path databaseFile, int busyTimeoutMillis, LogPort log) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        try {
            Files.createDirectories(databaseFile.getParent());
        } catch (IOException failure) {
            throw new UncheckedIOException("Dossier de base de données impossible à créer", failure);
        }
        try {
            // Le driver est chargé par le LibraryLoader de Paper : on force son enregistrement.
            Class.forName("org.sqlite.JDBC");
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            connection.setAutoCommit(true);
            SqliteDatabase database = new SqliteDatabase(connection, log);
            database.applyPragmas(busyTimeoutMillis);
            database.migrate();
            return database;
        } catch (ClassNotFoundException missingDriver) {
            throw new IllegalStateException("Driver SQLite introuvable : vérifiez la section libraries "
                    + "de plugin.yml et l'accès réseau au premier démarrage", missingDriver);
        } catch (SQLException failure) {
            throw new IllegalStateException("Ouverture de la base SQLite impossible", failure);
        }
    }

    private void applyPragmas(int busyTimeoutMillis) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = FULL");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + busyTimeoutMillis);
        }
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version    INTEGER PRIMARY KEY,
                        script     TEXT    NOT NULL,
                        applied_at INTEGER NOT NULL
                    )""");
        }

        int applied = currentVersion();
        for (int version = applied + 1; version <= MIGRATIONS.size(); version++) {
            String script = MIGRATIONS.get(version - 1);
            log.info("Application de la migration", "version", version, "script", script);
            runScript(readMigration(script));
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_version (version, script, applied_at) VALUES (?, ?, ?)")) {
                insert.setInt(1, version);
                insert.setString(2, script);
                insert.setLong(3, System.currentTimeMillis());
                insert.executeUpdate();
            }
        }
    }

    private int currentVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private void runScript(String script) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
            connection.commit();
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static String readMigration(String script) {
        String resource = "/dls/migrations/" + script;
        try (InputStream stream = SqliteDatabase.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Migration absente du JAR : " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Lecture de migration impossible : " + resource, failure);
        }
    }

    public Connection connection() {
        return connection;
    }

    /** Exécute un bloc dans une transaction, avec rollback en cas d'échec. */
    public <T> T inTransaction(TransactionalWork<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure instanceof SQLException sql
                        ? new IllegalStateException("Transaction annulée", sql)
                        : (RuntimeException) failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Transaction impossible", failure);
        }
    }

    @Override
    public void close() {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException failure) {
            log.warn("Fermeture de la base imparfaite", "error", failure.getMessage());
        }
    }

    @FunctionalInterface
    public interface TransactionalWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
