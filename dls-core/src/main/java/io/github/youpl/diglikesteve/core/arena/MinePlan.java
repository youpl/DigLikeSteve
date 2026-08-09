package io.github.youpl.diglikesteve.core.arena;

import java.util.Objects;

/**
 * Plan de génération immuable : pour chaque index du cuboïde, l'identifiant de palette
 * initialement généré. C'est la seule source de vérité du score (DAT §7.1) : le matériau
 * réellement visible en jeu n'est jamais une preuve suffisante.
 */
public final class MinePlan {

    private final ArenaBounds bounds;
    private final BlockPalette palette;
    private final byte[] types;
    private final long seed;

    MinePlan(ArenaBounds bounds, BlockPalette palette, byte[] types, long seed) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.palette = Objects.requireNonNull(palette, "palette");
        this.types = Objects.requireNonNull(types, "types");
        this.seed = seed;
        if (types.length != bounds.volume()) {
            throw new IllegalArgumentException("Taille du plan incohérente avec le volume");
        }
    }

    public ArenaBounds bounds() {
        return bounds;
    }

    public BlockPalette palette() {
        return palette;
    }

    public long seed() {
        return seed;
    }

    public int volume() {
        return types.length;
    }

    public byte typeAt(int index) {
        return types[index];
    }

    public int pointsAt(int index) {
        return palette.pointsOf(types[index]);
    }

    public String materialKeyAt(int index) {
        return palette.materialKey(types[index]);
    }

    /** Remplace une position par de l'air non scorable (poche de spawn). */
    void clearToAir(int index) {
        types[index] = BlockPalette.AIR_ID;
    }

    /** Échange deux types. Utilisé pour éloigner un bloc dangereux sans altérer les quotas. */
    void swap(int first, int second) {
        byte tmp = types[first];
        types[first] = types[second];
        types[second] = tmp;
    }

    /** Estimation mémoire en octets, contrôlée avant allocation (DAT §15). */
    public static long estimatedBytes(long volume) {
        // byte[] des types + AtomicLongArray du bitset consommé.
        return volume + ((volume + 63L) / 64L) * 8L;
    }
}
