package io.github.youpl.diglikesteve.platform.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Clés du conteneur de données persistant (DAT §7.2 et §9).
 *
 * <p>Les outils DLS ne sont jamais identifiés par leur nom ou leur lore, qui sont falsifiables,
 * mais par ces clés PDC portant l'identifiant de session.</p>
 */
public final class DlsKeys {

    private final NamespacedKey tntOwner;
    private final NamespacedKey sessionId;
    private final NamespacedKey toolType;
    private final NamespacedKey rewardKey;
    private final NamespacedKey guiAction;

    public DlsKeys(Plugin plugin) {
        this.tntOwner = new NamespacedKey(plugin, "owner");
        this.sessionId = new NamespacedKey(plugin, "session");
        this.toolType = new NamespacedKey(plugin, "tool");
        this.rewardKey = new NamespacedKey(plugin, "reward");
        this.guiAction = new NamespacedKey(plugin, "gui_action");
    }

    /** Propriétaire d'une TNT amorcée, stocké sur l'entité. */
    public NamespacedKey tntOwner() {
        return tntOwner;
    }

    /** Session d'appartenance d'un item ou d'une entité. */
    public NamespacedKey sessionId() {
        return sessionId;
    }

    /** Type d'outil du kit : pickaxe, axe, shovel, shears, igniter. */
    public NamespacedKey toolType() {
        return toolType;
    }

    public NamespacedKey rewardKey() {
        return rewardKey;
    }

    public NamespacedKey guiAction() {
        return guiAction;
    }
}
