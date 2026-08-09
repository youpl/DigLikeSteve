package io.github.youpl.diglikesteve.platform.paper;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Journalisation structurée (DAT §19) : les paires clé/valeur sont rendues en suffixe, et le
 * contenu des inventaires n'est jamais sérialisé.
 */
public final class PaperLogAdapter implements LogPort {

    private final Logger logger;
    private final boolean debugEnabled;

    public PaperLogAdapter(Logger logger, boolean debugEnabled) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.debugEnabled = debugEnabled;
    }

    @Override
    public void info(String message, Object... context) {
        logger.info(format(message, context));
    }

    @Override
    public void warn(String message, Object... context) {
        logger.warning(format(message, context));
    }

    @Override
    public void error(String message, Throwable throwable, Object... context) {
        if (throwable == null) {
            logger.severe(format(message, context));
        } else {
            logger.log(Level.SEVERE, format(message, context), throwable);
        }
    }

    @Override
    public void debug(String message, Object... context) {
        if (debugEnabled) {
            logger.info("[debug] " + format(message, context));
        }
    }

    private static String format(String message, Object... context) {
        if (context == null || context.length == 0) {
            return message;
        }
        StringBuilder builder = new StringBuilder(message).append(" {");
        for (int i = 0; i + 1 < context.length; i += 2) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(context[i]).append('=').append(context[i + 1]);
        }
        return builder.append('}').toString();
    }
}
