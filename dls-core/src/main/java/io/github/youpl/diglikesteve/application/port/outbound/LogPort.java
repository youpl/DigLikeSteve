package io.github.youpl.diglikesteve.application.port.outbound;

/**
 * Journalisation structurée (DAT §19). Les messages portent {@code sessionId}, {@code playerId},
 * {@code snapshotId} ou {@code tntId}, jamais le contenu d'un inventaire.
 */
public interface LogPort {

    void info(String message, Object... context);

    void warn(String message, Object... context);

    void error(String message, Throwable throwable, Object... context);

    void debug(String message, Object... context);
}
