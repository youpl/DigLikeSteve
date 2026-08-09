package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.arena.ArenaBounds;

import java.util.Objects;

/**
 * Lot d'écriture destiné à une seule région (DAT §6.3).
 *
 * <p>Le lot ne transporte que des primitives et des identifiants de palette : aucun objet
 * Bukkit ne franchit la frontière de thread (DAT §10.1).</p>
 *
 * @param bounds     bornes de l'arène, pour décoder les index
 * @param chunkX     coordonnée de chunk propriétaire du lot
 * @param chunkZ     coordonnée de chunk propriétaire du lot
 * @param indices    index compacts des positions à écrire
 * @param paletteIds identifiants de palette correspondants
 * @param length     nombre d'entrées valides dans les deux tableaux
 */
public record BlockBatch(ArenaBounds bounds, int chunkX, int chunkZ, int[] indices, byte[] paletteIds, int length) {

    public BlockBatch {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(indices, "indices");
        Objects.requireNonNull(paletteIds, "paletteIds");
        if (length < 0 || length > indices.length || length > paletteIds.length) {
            throw new IllegalArgumentException("Longueur de lot incohérente");
        }
    }
}
