package io.github.youpl.diglikesteve.core.arena;

import io.github.youpl.diglikesteve.core.shared.BlockPos;

import java.util.Objects;

/**
 * Cuboïde normalisé d'un seul monde. Fournit le mapping bijectif position &lt;-&gt; index compact
 * utilisé par le plan de mine et le bitset anti-double-score (DAT §6.5).
 */
public record ArenaBounds(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Volume maximal absolu : l'index compact doit tenir dans un {@code int}. */
    public static final long ABSOLUTE_MAX_VOLUME = Integer.MAX_VALUE - 8L;

    public ArenaBounds {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("Le nom de monde ne peut pas être vide");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Bornes non normalisées");
        }
    }

    /** Construit des bornes normalisées à partir de deux coins quelconques. */
    public static ArenaBounds of(String worldName, BlockPos a, BlockPos b) {
        return new ArenaBounds(
                worldName,
                Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** Volume en {@code long} : le dépassement doit être détecté avant toute allocation. */
    public long volumeLong() {
        return (long) sizeX() * (long) sizeY() * (long) sizeZ();
    }

    /**
     * Volume utilisable en {@code int}.
     *
     * @throws IllegalStateException si le cuboïde dépasse la capacité d'indexation
     */
    public int volume() {
        long volume = volumeLong();
        if (volume > ABSOLUTE_MAX_VOLUME) {
            throw new IllegalStateException("Volume d'arène non indexable : " + volume);
        }
        return (int) volume;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.x(), pos.y(), pos.z());
    }

    /**
     * Index compact d'une position contenue dans le cuboïde.
     *
     * @return l'index, ou {@code -1} si la position est hors bornes
     */
    public int index(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return -1;
        }
        int dx = x - minX;
        int dy = y - minY;
        int dz = z - minZ;
        return (dx * sizeY() + dy) * sizeZ() + dz;
    }

    public int index(BlockPos pos) {
        return index(pos.x(), pos.y(), pos.z());
    }

    /** Inverse de {@link #index(int, int, int)}. */
    public BlockPos positionOf(int index) {
        int sizeZ = sizeZ();
        int sizeY = sizeY();
        if (index < 0 || index >= volume()) {
            throw new IndexOutOfBoundsException("Index hors arène : " + index);
        }
        int dz = index % sizeZ;
        int rest = index / sizeZ;
        int dy = rest % sizeY;
        int dx = rest / sizeY;
        return new BlockPos(minX + dx, minY + dy, minZ + dz);
    }

    public int minChunkX() {
        return minX >> 4;
    }

    public int maxChunkX() {
        return maxX >> 4;
    }

    public int minChunkZ() {
        return minZ >> 4;
    }

    public int maxChunkZ() {
        return maxZ >> 4;
    }

    public int chunkCount() {
        return (maxChunkX() - minChunkX() + 1) * (maxChunkZ() - minChunkZ() + 1);
    }

    public BlockPos center() {
        return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    @Override
    public String toString() {
        return worldName + " [" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]";
    }
}
