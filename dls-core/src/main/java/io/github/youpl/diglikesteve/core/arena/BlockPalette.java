package io.github.youpl.diglikesteve.core.arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Palette compacte : chaque type de bloc reçoit un identifiant tenant sur un octet, ce qui
 * permet de mémoriser un million de positions dans un mégaoctet (DAT §6.5).
 *
 * <p>L'identifiant {@link #AIR_ID} est réservé aux positions non scorables (poches de spawn).</p>
 */
public final class BlockPalette {

    public static final byte AIR_ID = 0;
    public static final String AIR_KEY = "minecraft:air";

    private static final BlockDefinition AIR = new BlockDefinition(AIR_KEY, 0.0d, 0, null, null);

    private final List<BlockDefinition> definitions;
    private final int[] points;

    private BlockPalette(List<BlockDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        this.points = new int[this.definitions.size()];
        for (int i = 0; i < this.definitions.size(); i++) {
            points[i] = this.definitions.get(i).points();
        }
    }

    /**
     * @param scorables définitions configurées, dans l'ordre de {@code blocks.yml}
     */
    public static BlockPalette of(List<BlockDefinition> scorables) {
        Objects.requireNonNull(scorables, "scorables");
        if (scorables.isEmpty()) {
            throw new IllegalArgumentException("La palette doit contenir au moins un bloc");
        }
        if (scorables.size() > 254) {
            throw new IllegalArgumentException("Palette limitée à 254 types de blocs");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (BlockDefinition definition : scorables) {
            if (!seen.add(definition.materialKey().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Bloc dupliqué dans la palette : " + definition.materialKey());
            }
        }
        List<BlockDefinition> all = new ArrayList<>(scorables.size() + 1);
        all.add(AIR);
        all.addAll(scorables);
        return new BlockPalette(all);
    }

    /** Nombre total d'entrées, air compris. */
    public int size() {
        return definitions.size();
    }

    public BlockDefinition definition(byte id) {
        return definitions.get(Byte.toUnsignedInt(id));
    }

    /** Valeur en points d'un identifiant de palette. Chemin chaud : aucun boxing. */
    public int pointsOf(byte id) {
        int index = Byte.toUnsignedInt(id);
        return index < points.length ? points[index] : 0;
    }

    public String materialKey(byte id) {
        return definition(id).materialKey();
    }

    /** Définitions scorables, sans l'air. */
    public List<BlockDefinition> scorables() {
        return definitions.subList(1, definitions.size());
    }

    /** Identifiant de palette d'une clé matériau, ou {@code -1}. */
    public int idOf(String materialKey) {
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).materialKey().equalsIgnoreCase(materialKey)) {
                return i;
            }
        }
        return -1;
    }

    public double totalWeight() {
        double total = 0.0d;
        for (BlockDefinition definition : scorables()) {
            total += definition.weightPercent();
        }
        return total;
    }
}
