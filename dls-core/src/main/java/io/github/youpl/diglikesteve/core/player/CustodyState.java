package io.github.youpl.diglikesteve.core.player;

/**
 * État persisté de la garde d'inventaire (DAT §8). La progression est strictement monotone :
 * l'inventaire n'est vidé qu'après {@link #CAPTURED}, et une restauration ne peut être
 * appliquée deux fois car {@link #RESTORED} est absorbant.
 */
public enum CustodyState {

    /** Snapshot persisté et vérifié, inventaire du joueur encore intact. */
    CAPTURED,
    /** Inventaire vidé et kit appliqué : le snapshot est désormais la seule source. */
    KIT_APPLIED,
    /** Joueur en jeu. */
    ACTIVE,
    /** Joueur déconnecté, en délai de grâce. */
    SUSPENDED,
    /** Restauration due, à exécuter dès que possible. */
    RESTORE_PENDING,
    /** Inventaire rendu et confirmé. État absorbant. */
    RESTORED,
    /** Snapshot abandonné avant toute modification de l'inventaire. */
    INVALIDATED;

    /** Vrai si l'inventaire réel du joueur a déjà été remplacé par le kit. */
    public boolean inventoryReplaced() {
        return this == KIT_APPLIED || this == ACTIVE || this == SUSPENDED || this == RESTORE_PENDING;
    }

    /** États repris par le {@code RecoveryService} au démarrage (DAT §8.5). */
    public boolean requiresRecovery() {
        return inventoryReplaced();
    }

    public boolean isTerminal() {
        return this == RESTORED || this == INVALIDATED;
    }
}
