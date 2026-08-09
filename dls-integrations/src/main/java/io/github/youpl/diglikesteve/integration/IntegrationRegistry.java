package io.github.youpl.diglikesteve.integration;

import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.configuration.model.IntegrationSettings;
import io.github.youpl.diglikesteve.integration.placeholderapi.DlsPlaceholderExpansion;
import io.github.youpl.diglikesteve.integration.warp.WarpBridgeListener;
import io.github.youpl.diglikesteve.integration.worldedit.WorldEditSelectionAdapter;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import io.github.youpl.diglikesteve.platform.paper.command.SelectionDraft;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Détection et câblage des intégrations facultatives (DAT §13.1).
 *
 * <p>Chaque intégration est détectée par nom, chargée dans un package isolé, désactivable en
 * configuration et remplaçable par un fallback natif. Une intégration qui échoue à s'initialiser
 * est simplement ignorée : la partie ne doit jamais en dépendre.</p>
 */
public final class IntegrationRegistry {

    private final Plugin plugin;
    private final DlsServices services;
    private final LogPort log;
    private final Map<String, String> active = new LinkedHashMap<>();
    private final List<Runnable> shutdownHooks = new ArrayList<>();

    public IntegrationRegistry(Plugin plugin, DlsServices services, LogPort log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "services");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Enregistre les intégrations activées.
     *
     * @param selection brouillon de sélection, auquel l'importateur WorldEdit est branché
     */
    public void enableAll(IntegrationSettings settings, SelectionDraft selection) {
        if (settings.worldEditSelection()
                && (isPluginEnabled("WorldEdit") || isPluginEnabled("FastAsyncWorldEdit"))) {
            enableSafely("WorldEdit/FAWE", () -> WorldEditSelectionAdapter.detect(log).ifPresent(adapter -> {
                selection.importer(adapter);
                active.put("worldedit-selection", "import de sélection");
            }));
        }

        // La détection précède le chargement de la classe : sans PlaceholderAPI, référencer
        // DlsPlaceholderExpansion déclencherait un NoClassDefFoundError inutile.
        if (settings.placeholderApi() && isPluginEnabled("PlaceholderAPI")) {
            enableSafely("PlaceholderAPI", () -> {
                DlsPlaceholderExpansion expansion =
                        new DlsPlaceholderExpansion(services, plugin.getPluginMeta().getVersion());
                if (expansion.register()) {
                    active.put("placeholderapi", "%dls_*%");
                    shutdownHooks.add(expansion::unregister);
                }
            });
        }

        if (!IntegrationSettings.WARP_MODE_DISABLED.equals(settings.warpBridgeMode())) {
            enableSafely("Pont de warp", () -> {
                WarpBridgeListener bridge = new WarpBridgeListener(services);
                plugin.getServer().getPluginManager().registerEvents(bridge, plugin);
                active.put("warp-bridge", settings.warpBridgeMode());
            });
        }

        if (active.isEmpty()) {
            log.info("Aucune intégration activée : le mode natif est utilisé");
        } else {
            active.forEach((name, detail) -> log.info("Intégration active", "nom", name, "detail", detail));
        }
    }

    /** Détection par nom, avant tout chargement de classe tierce (DAT §13.1). */
    private boolean isPluginEnabled(String pluginName) {
        var detected = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return detected != null && detected.isEnabled();
    }

    /**
     * Une intégration qui échoue est désactivée localement, sans casser le démarrage
     * (disjoncteur simple, DAT §4.3).
     */
    private void enableSafely(String name, Runnable initialisation) {
        try {
            initialisation.run();
        } catch (Throwable failure) {
            log.warn("Intégration désactivée après échec", "nom", name, "erreur", String.valueOf(failure));
        }
    }

    public Map<String, String> active() {
        return Map.copyOf(active);
    }

    /** Désenregistre proprement : aucun singleton statique ne doit survivre (DAT §15). */
    public void disableAll() {
        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (Throwable ignored) {
                // Le plugin tiers peut déjà être déchargé.
            }
        }
        shutdownHooks.clear();
        active.clear();
    }
}
