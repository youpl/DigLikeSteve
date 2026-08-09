package io.github.youpl.diglikesteve.core.arena;

/**
 * Paramètres de réservation des poches de spawn (DAT §6.4).
 *
 * @param count            nombre de slots à réserver, donc de participants simultanés maximum
 * @param pocketWidth      largeur et profondeur de la poche, impaire (3 recommandé)
 * @param pocketHeight     hauteur libre au-dessus des pieds (2 minimum)
 * @param minDistance      distance minimale entre deux poches
 * @param hazardSafeRadius rayon dans lequel aucun bloc dangereux (TNT) ne doit subsister
 * @param maxAttempts      nombre maximal de tirages avant abandon
 */
public record SpawnSlotSettings(
        int count,
        int pocketWidth,
        int pocketHeight,
        int minDistance,
        int hazardSafeRadius,
        int maxAttempts) {

    public SpawnSlotSettings {
        if (count <= 0) {
            throw new IllegalArgumentException("count doit être positif");
        }
        if (pocketWidth < 1 || pocketWidth % 2 == 0) {
            throw new IllegalArgumentException("pocketWidth doit être impair et >= 1");
        }
        if (pocketHeight < 2) {
            throw new IllegalArgumentException("pocketHeight doit valoir au moins 2");
        }
        if (minDistance < 0 || hazardSafeRadius < 0) {
            throw new IllegalArgumentException("Distances négatives interdites");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts doit être positif");
        }
    }

    public static SpawnSlotSettings defaults(int count) {
        return new SpawnSlotSettings(count, 3, 2, 6, 2, 20_000);
    }

    public int halfWidth() {
        return pocketWidth / 2;
    }
}
