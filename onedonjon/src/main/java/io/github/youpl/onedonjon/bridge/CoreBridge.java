package io.github.youpl.onedonjon.bridge;

import org.bukkit.permissions.Permissible;

/**
 * Point d'entree unique vers la plateforme d'administration (OneCore) : configuration centralisee,
 * droits d'administration et journal d'audit. Le reste du plugin ne connait que cette interface.
 * <p>Sans OneCore, {@link StandaloneCore} fournit le meme service avec la configuration locale.
 */
public interface CoreBridge {

    /** Nom affichable de l'implementation active. */
    String name();

    /** Vrai si OneCore pilote reellement la configuration / l'audit. */
    boolean connected();

    /** Droit d'administration OneDonjon (edition des donjons). */
    boolean isAdmin(Permissible who);

    /** Droit precis (ex. {@code onedonjon.admin.commands}). */
    boolean has(Permissible who, String permission);

    /** Lit un reglage : valeur OneCore si presente, sinon config.yml local, sinon {@code def}. */
    String setting(String path, String def);

    default int settingInt(String path, int def) {
        try {
            return Integer.parseInt(setting(path, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    default boolean settingBool(String path, boolean def) {
        return Boolean.parseBoolean(setting(path, String.valueOf(def)).trim());
    }

    /** Enregistre un rappel appele quand la configuration change (rechargement OneCore ou local). */
    void onReload(Runnable callback);

    /** Trace une action d'administration (qui, quoi, detail). Ne leve jamais d'exception. */
    void audit(String actor, String action, String detail);

    /** Libere les ressources (fichier d'audit...). */
    void close();
}
