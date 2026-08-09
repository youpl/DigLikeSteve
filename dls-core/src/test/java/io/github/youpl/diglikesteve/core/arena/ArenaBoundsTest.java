package io.github.youpl.diglikesteve.core.arena;

import io.github.youpl.diglikesteve.core.shared.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mapping cuboïde/index (DAT §21). */
class ArenaBoundsTest {

    @Test
    @DisplayName("les coins sont normalisés quel que soit l'ordre de saisie")
    void normalizesCorners() {
        ArenaBounds bounds = ArenaBounds.of("world", new BlockPos(10, 70, -3), new BlockPos(-2, 60, 8));
        assertEquals(-2, bounds.minX());
        assertEquals(60, bounds.minY());
        assertEquals(-3, bounds.minZ());
        assertEquals(10, bounds.maxX());
        assertEquals(70, bounds.maxY());
        assertEquals(8, bounds.maxZ());
    }

    @Test
    @DisplayName("index et position sont bijectifs sur tout le volume")
    void indexIsBijective() {
        ArenaBounds bounds = ArenaBounds.of("world", new BlockPos(-5, 60, 7), new BlockPos(3, 68, 15));
        int volume = bounds.volume();
        assertEquals(9 * 9 * 9, volume);

        boolean[] seen = new boolean[volume];
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    int index = bounds.index(x, y, z);
                    assertTrue(index >= 0 && index < volume, "index hors bornes");
                    assertFalse(seen[index], "collision d'index");
                    seen[index] = true;
                    assertEquals(new BlockPos(x, y, z), bounds.positionOf(index));
                }
            }
        }
    }

    @Test
    @DisplayName("une position hors cuboïde retourne -1")
    void outsideReturnsMinusOne() {
        ArenaBounds bounds = ArenaBounds.of("world", new BlockPos(0, 60, 0), new BlockPos(4, 64, 4));
        assertEquals(-1, bounds.index(5, 62, 2));
        assertEquals(-1, bounds.index(2, 59, 2));
        assertFalse(bounds.contains(-1, 62, 2));
    }

    @Test
    @DisplayName("le volume est calculé en long avant toute allocation")
    void volumeIsCheckedInLong() {
        // 3001 × 385 × 3001 dépasse largement la capacité d'un index int.
        ArenaBounds huge = ArenaBounds.of("world", new BlockPos(0, -64, 0), new BlockPos(3_000, 320, 3_000));
        assertTrue(huge.volumeLong() > ArenaBounds.ABSOLUTE_MAX_VOLUME);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, huge::volume);
    }
}
