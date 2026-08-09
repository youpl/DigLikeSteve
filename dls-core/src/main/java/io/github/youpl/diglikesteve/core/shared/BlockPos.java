package io.github.youpl.diglikesteve.core.shared;

/** Position entière d'un bloc, sans référence de monde. */
public record BlockPos(int x, int y, int z) {

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /** Distance euclidienne au carré, en entiers longs pour éviter tout dépassement. */
    public long distanceSquared(BlockPos other) {
        long dx = (long) x - other.x;
        long dy = (long) y - other.y;
        long dz = (long) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
