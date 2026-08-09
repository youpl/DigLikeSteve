package io.github.youpl.diglikesteve.core.arena;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Génération exacte du plan de mine (DAT §6.2).
 *
 * <p>La méthode des plus forts restes fixe d'abord le nombre exact de blocs de chaque type,
 * puis un mélange de Fisher-Yates déterministe distribue ces identifiants dans le cuboïde.
 * Une même graine reproduit donc exactement la même mine, et la somme des quotas est toujours
 * égale au volume — contrairement à une suite de tirages indépendants.</p>
 *
 * <p>Ce calcul est pur : il tourne sur l'exécuteur CPU privé, jamais sur un thread de région.</p>
 */
public final class MinePlanFactory {

    private MinePlanFactory() {
    }

    /**
     * @param bounds  cuboïde cible
     * @param palette palette validée (la somme des poids doit valoir 100 %)
     * @param seed    graine de session
     */
    public static MinePlan generate(ArenaBounds bounds, BlockPalette palette, long seed) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(palette, "palette");

        int volume = bounds.volume();
        int[] quotas = computeQuotas(palette, volume);

        byte[] types = new byte[volume];
        int cursor = 0;
        for (int paletteId = 1; paletteId < palette.size(); paletteId++) {
            int count = quotas[paletteId];
            byte id = (byte) paletteId;
            Arrays.fill(types, cursor, cursor + count, id);
            cursor += count;
        }
        if (cursor != volume) {
            throw new IllegalStateException("Quotas incohérents : " + cursor + " != " + volume);
        }

        shuffle(types, new SplittableRandom(seed));

        return new MinePlan(bounds, palette, types, seed);
    }

    /**
     * Répartition entière exacte par la méthode des plus forts restes.
     *
     * @return tableau indexé par identifiant de palette ; l'index 0 (air) vaut toujours 0
     */
    public static int[] computeQuotas(BlockPalette palette, int volume) {
        int paletteSize = palette.size();
        int[] quotas = new int[paletteSize];
        if (volume <= 0) {
            return quotas;
        }

        double totalWeight = palette.totalWeight();
        if (totalWeight <= 0.0d) {
            throw new IllegalArgumentException("La somme des poids de la palette est nulle");
        }

        List<Remainder> remainders = new ArrayList<>(paletteSize - 1);
        long assigned = 0L;
        for (int paletteId = 1; paletteId < paletteSize; paletteId++) {
            double share = palette.definition((byte) paletteId).weightPercent() / totalWeight;
            double exact = share * volume;
            int floor = (int) Math.floor(exact);
            quotas[paletteId] = floor;
            assigned += floor;
            remainders.add(new Remainder(paletteId, exact - floor));
        }

        int leftover = volume - (int) assigned;
        // Départage stable : plus fort reste, puis identifiant de palette croissant.
        remainders.sort(Comparator
                .comparingDouble(Remainder::fraction).reversed()
                .thenComparingInt(Remainder::paletteId));
        for (int i = 0; i < leftover; i++) {
            quotas[remainders.get(i % remainders.size()).paletteId()]++;
        }
        return quotas;
    }

    /** Fisher-Yates classique, du dernier index vers le premier. */
    static void shuffle(byte[] values, RandomGenerator random) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            byte tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
    }

    private record Remainder(int paletteId, double fraction) {
    }
}
