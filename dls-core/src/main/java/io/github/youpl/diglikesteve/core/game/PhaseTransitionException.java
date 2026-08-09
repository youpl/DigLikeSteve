package io.github.youpl.diglikesteve.core.game;

/** Transition refusée par la machine à états. */
public final class PhaseTransitionException extends IllegalStateException {

    private final SessionPhase from;
    private final SessionPhase to;

    public PhaseTransitionException(SessionPhase from, SessionPhase to) {
        super("Transition interdite : " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public SessionPhase from() {
        return from;
    }

    public SessionPhase to() {
        return to;
    }
}
