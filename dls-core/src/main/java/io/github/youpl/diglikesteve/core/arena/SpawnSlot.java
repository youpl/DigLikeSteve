package io.github.youpl.diglikesteve.core.arena;

import io.github.youpl.diglikesteve.core.shared.BlockPos;

import java.util.List;
import java.util.Objects;

/**
 * Poche d'air réservée à un participant (DAT §6.4).
 *
 * @param feet    position des pieds du joueur
 * @param yaw     orientation d'arrivée
 * @param cleared positions du cuboïde vidées pour former la poche ; elles ne rapportent jamais
 */
public record SpawnSlot(BlockPos feet, float yaw, List<BlockPos> cleared) {

    public SpawnSlot {
        Objects.requireNonNull(feet, "feet");
        cleared = List.copyOf(Objects.requireNonNull(cleared, "cleared"));
    }
}
