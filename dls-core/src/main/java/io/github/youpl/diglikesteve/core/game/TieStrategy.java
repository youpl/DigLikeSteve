package io.github.youpl.diglikesteve.core.game;

/** Stratégie de départage des ex æquo (DAT §2). */
public enum TieStrategy {

    /** Tous les ex æquo sont récompensés. Valeur par défaut. */
    SHARED,
    /** Le premier à avoir atteint le score gagne seul. */
    FIRST_REACHED,
    /** Un seul gagnant tiré au sort, de façon reproductible depuis la graine de session. */
    RANDOM
}
