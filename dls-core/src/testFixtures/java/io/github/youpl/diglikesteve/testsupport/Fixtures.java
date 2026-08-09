package io.github.youpl.diglikesteve.testsupport;

import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.arena.BlockDefinition;
import io.github.youpl.diglikesteve.core.arena.BlockPalette;
import io.github.youpl.diglikesteve.core.shared.BlockPos;

import java.util.List;

/** Fixtures partagées par les tests (DAT §4.2, module {@code dls-test-support}). */
public final class Fixtures {

    private Fixtures() {
    }

    /** Palette calquée sur {@code blocks.yml} par défaut : la somme des poids vaut 100. */
    public static BlockPalette defaultPalette() {
        return BlockPalette.of(List.of(
                BlockDefinition.of("minecraft:stone", 40.0d, 3),
                BlockDefinition.of("minecraft:cobblestone", 25.0d, 5),
                BlockDefinition.of("minecraft:ice", 12.0d, 1),
                BlockDefinition.of("minecraft:oak_log", 10.0d, 4),
                BlockDefinition.of("minecraft:melon", 7.0d, 5),
                BlockDefinition.of("minecraft:tnt", 4.0d, 0),
                BlockDefinition.of("minecraft:iron_block", 2.0d, 10)));
    }

    public static ArenaBounds bounds(int size) {
        return ArenaBounds.of("world", new BlockPos(0, 60, 0),
                new BlockPos(size - 1, 60 + size - 1, size - 1));
    }

    /** Identifiants de palette considérés comme dangereux près d'un spawn. */
    public static boolean[] tntHazard(BlockPalette palette) {
        boolean[] hazardous = new boolean[palette.size()];
        int tnt = palette.idOf("minecraft:tnt");
        if (tnt > 0) {
            hazardous[tnt] = true;
        }
        return hazardous;
    }
}
