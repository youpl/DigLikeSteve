package io.github.youpl.diglikesteve.configuration.model;

import java.util.Locale;
import java.util.Set;

/**
 * Réglages TNT (DAT §7.2).
 *
 * @param allowedPrimeCauses causes d'amorçage créditées ; par défaut le joueur seul
 * @param requireDlsIgniter  n'attribuer une TNT que si le briquet marqué DLS a été utilisé
 * @param ownershipTtlSeconds durée de vie du registre de propriété
 * @param accountTtlSeconds  durée de vie d'un compte d'explosion non réglé
 * @param confineDamageToArena annuler les dégâts de blocs hors du cuboïde
 * @param removeExplosionDrops supprimer les items lâchés par une explosion
 */
public record TntSettings(
        Set<String> allowedPrimeCauses,
        boolean requireDlsIgniter,
        long ownershipTtlSeconds,
        long accountTtlSeconds,
        boolean confineDamageToArena,
        boolean removeExplosionDrops) {

    public TntSettings {
        allowedPrimeCauses = Set.copyOf(allowedPrimeCauses);
        if (ownershipTtlSeconds <= 0L || accountTtlSeconds <= 0L) {
            throw new IllegalArgumentException("Les durées de vie TNT doivent être positives");
        }
    }

    public static TntSettings defaults() {
        return new TntSettings(Set.of("PLAYER"), true, 120L, 30L, true, true);
    }

    public boolean allows(String primeCause) {
        return allowedPrimeCauses.contains(primeCause.toUpperCase(Locale.ROOT));
    }
}
