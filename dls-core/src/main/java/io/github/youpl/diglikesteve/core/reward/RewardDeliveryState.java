package io.github.youpl.diglikesteve.core.reward;

/** États de l'outbox de récompense (DAT §12). */
public enum RewardDeliveryState {

    /** Écrit en base, pas encore remis au fournisseur. */
    PENDING,
    /** Remis avec succès, ou marqué manuellement par un administrateur. */
    DELIVERED,
    /** Le fournisseur a échoué ; l'entrée reste rejouable. */
    FAILED,
    /** Gagnant hors ligne : la remise attend sa prochaine connexion. */
    MAILBOX
}
