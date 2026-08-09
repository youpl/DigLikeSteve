package io.github.youpl.diglikesteve.configuration.model;

import java.util.List;
import java.util.Objects;

/**
 * Réglages de récompense (DAT §12).
 *
 * @param strategy      {@code inventory-key}, {@code console-command} ou {@code provider-api}
 * @param commands      commandes console, avec {@code %player%}, {@code %uuid%} et {@code %session%}
 * @param itemMaterial  matériau de la clé remise par {@code inventory-key}
 * @param itemName      nom MiniMessage de la clé
 * @param trackWins     incrémente le compteur permanent de victoires
 * @param retryPeriodSeconds période de rejeu automatique de l'outbox
 */
public record RewardSettings(
        String strategy,
        List<String> commands,
        String itemMaterial,
        String itemName,
        boolean trackWins,
        long retryPeriodSeconds) {

    public RewardSettings {
        Objects.requireNonNull(strategy, "strategy");
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        if (retryPeriodSeconds <= 0L) {
            throw new IllegalArgumentException("retryPeriodSeconds doit être positif");
        }
    }

    public static RewardSettings defaults() {
        return new RewardSettings("console-command",
                List.of("crate give %player% dls-key 1"),
                "TRIPWIRE_HOOK",
                "<gold>Clé Dig Like Steve</gold>",
                true,
                60L);
    }
}
