package io.github.youpl.diglikesteve.core.game;

import java.util.Set;

/**
 * Machine à états de la session (DAT §5.2). Les transitions autorisées sont déclarées ici,
 * validées par comparaison atomique dans {@link GameSession#transition}.
 */
public enum SessionPhase {

    IDLE,
    PREPARING,
    REGISTRATION,
    STARTING,
    RUNNING,
    FINISHING,
    RESTORING,
    CLEANUP,
    RECOVERY;

    private static final Set<SessionPhase> FROM_IDLE = Set.of(PREPARING, RECOVERY);
    private static final Set<SessionPhase> FROM_PREPARING = Set.of(REGISTRATION, CLEANUP, RECOVERY);
    private static final Set<SessionPhase> FROM_REGISTRATION = Set.of(STARTING, FINISHING, CLEANUP, RECOVERY);
    private static final Set<SessionPhase> FROM_STARTING = Set.of(RUNNING, FINISHING, RECOVERY);
    private static final Set<SessionPhase> FROM_RUNNING = Set.of(FINISHING, RECOVERY);
    private static final Set<SessionPhase> FROM_FINISHING = Set.of(RESTORING, RECOVERY);
    private static final Set<SessionPhase> FROM_RESTORING = Set.of(CLEANUP, RECOVERY);
    private static final Set<SessionPhase> FROM_CLEANUP = Set.of(IDLE);
    private static final Set<SessionPhase> FROM_RECOVERY = Set.of(CLEANUP, IDLE);

    public boolean canTransitionTo(SessionPhase target) {
        return switch (this) {
            case IDLE -> FROM_IDLE.contains(target);
            case PREPARING -> FROM_PREPARING.contains(target);
            case REGISTRATION -> FROM_REGISTRATION.contains(target);
            case STARTING -> FROM_STARTING.contains(target);
            case RUNNING -> FROM_RUNNING.contains(target);
            case FINISHING -> FROM_FINISHING.contains(target);
            case RESTORING -> FROM_RESTORING.contains(target);
            case CLEANUP -> FROM_CLEANUP.contains(target);
            case RECOVERY -> FROM_RECOVERY.contains(target);
        };
    }

    /** Une session est active dès qu'elle n'est plus au repos. */
    public boolean isActive() {
        return this != IDLE;
    }

    /** Seule cette phase autorise l'inscription de nouveaux joueurs. */
    public boolean acceptsRegistration() {
        return this == REGISTRATION;
    }

    /** Seule cette phase autorise le score. */
    public boolean acceptsScoring() {
        return this == RUNNING;
    }

    /** Les modifications d'arène, de blocs ou de durée n'ont lieu qu'au repos (DAT §6.1). */
    public boolean acceptsConfigurationChange() {
        return this == IDLE;
    }
}
