package io.github.youpl.diglikesteve.configuration.model;

import java.util.Objects;

/**
 * Activation des intégrations facultatives (DAT §13). Chacune est désactivable et remplaçable
 * par un fallback natif : DLS doit démarrer sans aucune d'entre elles.
 *
 * @param worldEditSelection    import de sélection WorldEdit/FAWE
 * @param placeholderApi        exposition des placeholders
 * @param miniPlaceholders      exposition MiniPlaceholders
 * @param warpBridgeMode        {@code disabled}, {@code destination} ou {@code exact-command}
 * @param warpName              nom du warp surveillé, par défaut {@code DLS}
 * @param warpTriggerRadius     rayon de déclenchement en mode destination
 * @param coreProtectSkipLogging demande à CoreProtect d'ignorer la zone DLS
 */
public record IntegrationSettings(
        boolean worldEditSelection,
        boolean placeholderApi,
        boolean miniPlaceholders,
        String warpBridgeMode,
        String warpName,
        double warpTriggerRadius,
        boolean coreProtectSkipLogging) {

    public static final String WARP_MODE_DISABLED = "disabled";
    public static final String WARP_MODE_DESTINATION = "destination";
    public static final String WARP_MODE_EXACT_COMMAND = "exact-command";

    public IntegrationSettings {
        Objects.requireNonNull(warpBridgeMode, "warpBridgeMode");
        Objects.requireNonNull(warpName, "warpName");
        if (warpTriggerRadius <= 0.0d) {
            throw new IllegalArgumentException("warpTriggerRadius doit être positif");
        }
    }

    public static IntegrationSettings defaults() {
        // L'interception de commandes est intrusive : elle reste désactivée par défaut.
        return new IntegrationSettings(true, true, true, WARP_MODE_DESTINATION, "DLS", 3.0d, false);
    }
}
