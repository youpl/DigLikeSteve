package io.github.youpl.diglikesteve.core.player;

/** Statut métier d'un participant (DAT §5.1). */
public enum ParticipantStatus {

    /** Inscription en cours : snapshot capturé, kit pas encore appliqué. */
    JOINING,
    /** En jeu, peut marquer des points. */
    ACTIVE,
    /** Déconnecté, dans son délai de grâce. */
    SUSPENDED,
    /** Délai de grâce dépassé : exclu du classement. */
    DISQUALIFIED,
    /** Partie terminée, en attente de restauration. */
    FINISHED,
    /** Inventaire rendu, plus rien à faire. */
    RESTORED;

    public boolean canScore() {
        return this == ACTIVE;
    }

    public boolean isRanked() {
        return this == ACTIVE || this == SUSPENDED || this == FINISHED || this == RESTORED;
    }

    public boolean isTerminal() {
        return this == RESTORED || this == DISQUALIFIED;
    }
}
