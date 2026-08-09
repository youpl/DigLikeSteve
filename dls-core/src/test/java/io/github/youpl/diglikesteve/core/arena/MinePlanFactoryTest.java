package io.github.youpl.diglikesteve.core.arena;

import io.github.youpl.diglikesteve.testsupport.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Distribution exacte et graine reproductible (DAT §21). */
class MinePlanFactoryTest {

    @Test
    @DisplayName("la somme des quotas vaut exactement le volume")
    void quotasSumToVolume() {
        BlockPalette palette = Fixtures.defaultPalette();
        for (int volume : new int[] {1, 7, 99, 1_000, 12_345, 262_144}) {
            int[] quotas = MinePlanFactory.computeQuotas(palette, volume);
            int total = 0;
            for (int quota : quotas) {
                assertTrue(quota >= 0, "quota négatif");
                total += quota;
            }
            assertEquals(volume, total, "volume " + volume);
        }
    }

    @Test
    @DisplayName("aucune position n'est laissée vide et les quotas sont respectés")
    void planMatchesQuotas() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(16);
        MinePlan plan = MinePlanFactory.generate(bounds, palette, 42L);

        int[] expected = MinePlanFactory.computeQuotas(palette, bounds.volume());
        Map<Byte, Integer> counted = new HashMap<>();
        for (int index = 0; index < plan.volume(); index++) {
            counted.merge(plan.typeAt(index), 1, Integer::sum);
        }
        for (int id = 1; id < palette.size(); id++) {
            assertEquals(expected[id], counted.getOrDefault((byte) id, 0),
                    "quota du type " + palette.materialKey((byte) id));
        }
        assertEquals(0, counted.getOrDefault(BlockPalette.AIR_ID, 0), "aucune position ne doit être vide");
    }

    @Test
    @DisplayName("une même graine reproduit exactement la même mine")
    void seedIsReproducible() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(12);

        byte[] first = dump(MinePlanFactory.generate(bounds, palette, 1234L));
        byte[] second = dump(MinePlanFactory.generate(bounds, palette, 1234L));
        byte[] other = dump(MinePlanFactory.generate(bounds, palette, 1235L));

        assertArrayEquals(first, second, "même graine, même mine");
        assertTrue(java.util.Arrays.compare(first, other) != 0, "graines différentes, mines différentes");
    }

    @Test
    @DisplayName("le mélange ne concentre pas un type au début du cuboïde")
    void shuffleSpreadsTypes() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(20);
        MinePlan plan = MinePlanFactory.generate(bounds, palette, 7L);

        byte rare = (byte) palette.idOf("minecraft:iron_block");
        int firstHalf = 0;
        int total = 0;
        for (int index = 0; index < plan.volume(); index++) {
            if (plan.typeAt(index) == rare) {
                total++;
                if (index < plan.volume() / 2) {
                    firstHalf++;
                }
            }
        }
        assertTrue(total > 0, "le fer doit être présent");
        double ratio = (double) firstHalf / total;
        // Un tri non mélangé donnerait 1.0 ou 0.0 ; on tolère un large écart statistique.
        assertTrue(ratio > 0.3d && ratio < 0.7d, "répartition déséquilibrée : " + ratio);
    }

    private static byte[] dump(MinePlan plan) {
        byte[] types = new byte[plan.volume()];
        for (int index = 0; index < types.length; index++) {
            types[index] = plan.typeAt(index);
        }
        return types;
    }
}
