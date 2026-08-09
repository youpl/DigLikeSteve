package io.github.youpl.diglikesteve.core.shared;

import java.util.Objects;

/** Position précise dans un monde nommé, sans objet de plateforme. */
public record WorldLocation(String worldName, double x, double y, double z, float yaw, float pitch) {

    public WorldLocation {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("Nom de monde vide");
        }
    }

    public BlockPos toBlockPos() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public static WorldLocation of(String worldName, double x, double y, double z) {
        return new WorldLocation(worldName, x, y, z, 0.0f, 0.0f);
    }
}
