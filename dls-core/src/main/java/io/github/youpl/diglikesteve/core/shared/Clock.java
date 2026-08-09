package io.github.youpl.diglikesteve.core.shared;

/**
 * Horloge injectée dans le domaine (DAT §5.3).
 *
 * <p>{@link #nanoTime()} est monotone et sert aux échéances en mémoire ; {@link #epochMillis()}
 * est l'horloge murale UTC utilisée pour la persistance et la récupération. Aucune échéance
 * ne dépend d'un compteur de ticks.</p>
 */
public interface Clock {

    long nanoTime();

    long epochMillis();

    /** Horloge système réelle. */
    static Clock system() {
        return new Clock() {
            @Override
            public long nanoTime() {
                return System.nanoTime();
            }

            @Override
            public long epochMillis() {
                return System.currentTimeMillis();
            }
        };
    }
}
