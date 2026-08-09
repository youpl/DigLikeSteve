package io.github.youpl.diglikesteve.core.tnt;

/**
 * Calcul pur de la pénalité TNT (DAT §7.3).
 *
 * <pre>
 * halfHearts = ceil(finalDamage - epsilon)
 * rate       = min(1.00, halfHearts * tauxParDemiCoeur)
 * penalty    = arrondi(grossPoints * rate)
 * netPoints  = grossPoints - penalty
 * </pre>
 *
 * <p>Dans l'API Minecraft, un point de dégât vaut un demi-cœur. L'epsilon absorbe les
 * imprécisions flottantes : 3.0000000001 dégât doit valoir 3 demi-cœurs, pas 4.</p>
 */
public final class TntPenaltyCalculator {

    /** Tolérance flottante sur les dégâts finaux. */
    public static final double EPSILON = 1.0e-6d;

    private TntPenaltyCalculator() {
    }

    /**
     * @param grossPoints       gain brut de l'explosion
     * @param finalDamage       dégâts finaux reçus par le propriétaire de la TNT
     * @param ratePerHalfHeart  part du gain retirée par demi-cœur (0.05 par défaut)
     * @param capPenaltyToGross la pénalité ne peut pas dépasser le gain de cette TNT : elle ne
     *                          retire donc jamais des points acquis auparavant
     */
    public static TntSettlement settle(
            long grossPoints,
            double finalDamage,
            double ratePerHalfHeart,
            boolean capPenaltyToGross) {
        if (grossPoints <= 0L) {
            return TntSettlement.zero();
        }
        int halfHearts = halfHearts(finalDamage);
        if (halfHearts <= 0) {
            return new TntSettlement(grossPoints, 0, 0.0d, 0L, grossPoints);
        }
        double rate = Math.min(1.0d, halfHearts * ratePerHalfHeart);
        long penalty = Math.round(grossPoints * rate);
        if (capPenaltyToGross) {
            penalty = Math.min(penalty, grossPoints);
        }
        return new TntSettlement(grossPoints, halfHearts, rate, penalty, grossPoints - penalty);
    }

    /** Conversion des dégâts finaux en demi-cœurs, arrondie au supérieur. */
    public static int halfHearts(double finalDamage) {
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0d) {
            return 0;
        }
        double ceiling = Math.ceil(finalDamage - EPSILON);
        if (ceiling >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) ceiling;
    }
}
