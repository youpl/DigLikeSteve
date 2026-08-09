package io.github.youpl.diglikesteve.core.arena;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Description d'un type de bloc de la mine (DAT §6.2). {@code materialKey} est une clé
 * Minecraft brute ; le domaine ne la résout jamais lui-même.
 */
public record BlockDefinition(
        String materialKey,
        double weightPercent,
        int points,
        String suggestedTool,
        Map<String, String> blockProperties) {

    public BlockDefinition {
        Objects.requireNonNull(materialKey, "materialKey");
        if (materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey vide");
        }
        if (weightPercent < 0.0d || !Double.isFinite(weightPercent)) {
            throw new IllegalArgumentException("Poids invalide pour " + materialKey + " : " + weightPercent);
        }
        if (points < 0) {
            throw new IllegalArgumentException("Points négatifs pour " + materialKey + " : " + points);
        }
        blockProperties = blockProperties == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(blockProperties));
    }

    public static BlockDefinition of(String materialKey, double weightPercent, int points) {
        return new BlockDefinition(materialKey, weightPercent, points, null, Map.of());
    }
}
