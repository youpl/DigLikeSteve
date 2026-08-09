package io.github.youpl.diglikesteve.core.tnt;

/**
 * Résultat du règlement d'une explosion.
 *
 * @param grossPoints valeur des blocs scorables effectivement consommés par cette TNT
 * @param halfHearts  demi-cœurs de dégâts finaux reçus par le propriétaire
 * @param rate        taux de pénalité appliqué, plafonné à 1.00
 * @param penalty     points retirés
 * @param netPoints   gain net crédité au propriétaire
 */
public record TntSettlement(long grossPoints, int halfHearts, double rate, long penalty, long netPoints) {

    public static TntSettlement zero() {
        return new TntSettlement(0L, 0, 0.0d, 0L, 0L);
    }
}
