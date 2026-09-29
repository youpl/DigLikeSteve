package io.github.youpl.onedonjon.bridge;

import java.util.UUID;
import java.util.logging.Level;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

/**
 * Adaptateur OneCore. Trouve le service d'API de OneCore via le ServicesManager de Bukkit puis l'appelle
 * par reflexion. Chaque capacite (config, admin, audit) est independante : si OneCore n'expose pas la
 * methode attendue, cette capacite retombe sur {@link StandaloneCore} et l'avertissement n'est emis qu'une fois.
 *
 * <p><b>Contrat suppose</b> (a ajuster aux noms reels de OneCore, voir les constantes) :
 * <pre>
 *   String  getConfigValue(String namespace, String path)     // null si absent
 *   boolean isAdmin(UUID player)
 *   void    audit(String namespace, String actor, String action, String detail)
 *   void    registerReloadListener(String namespace, Runnable callback)
 * </pre>
 */
public final class OneCoreAdapter extends StandaloneCore {

    // Noms a adapter a l'API reelle de OneCore.
    static final String M_CONFIG = "getConfigValue";
    static final String M_ADMIN = "isAdmin";
    static final String M_AUDIT = "audit";
    static final String M_RELOAD = "registerReloadListener";

    private final Object api;
    private final String namespace;
    private boolean configOk = true;
    private boolean adminOk = true;
    private boolean auditOk = true;

    private OneCoreAdapter(OneDonjonPlugin plugin, Object api, String namespace) {
        super(plugin);
        this.api = api;
        this.namespace = namespace;
        try {
            Reflect.call(api, M_RELOAD, namespace, (Runnable) this::fireReload);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("OneCore : " + M_RELOAD + " indisponible, rechargement manuel uniquement.");
        }
    }

    /** @return l'adaptateur, ou null si OneCore ou son API est introuvable. */
    public static OneCoreAdapter tryCreate(OneDonjonPlugin plugin) {
        if (!plugin.getConfig().getBoolean("onecore.enabled", true)) return null;
        var core = Bukkit.getPluginManager().getPlugin("OneCore");
        if (core == null || !core.isEnabled()) return null;
        String apiClass = plugin.getConfig().getString("onecore.api-class", "");
        Class<?> c = apiClass.isBlank() ? null : Reflect.cls(apiClass);
        if (c == null) {
            plugin.getLogger().warning("OneCore detecte mais classe d'API introuvable ('onecore.api-class' = '" + apiClass + "').");
            return null;
        }
        Object svc = Bukkit.getServicesManager().load(c);
        if (svc == null) {
            plugin.getLogger().warning("OneCore detecte mais aucun service enregistre pour " + apiClass);
            return null;
        }
        return new OneCoreAdapter(plugin, svc, plugin.getConfig().getString("onecore.namespace", "onedonjon"));
    }

    @Override public String name() { return "OneCore"; }
    @Override public boolean connected() { return true; }

    @Override
    public boolean isAdmin(Permissible who) {
        // Le droit Bukkit reste necessaire ; OneCore peut seulement le restreindre, jamais l'elargir.
        if (!super.isAdmin(who)) return false;
        if (!adminOk || !(who instanceof Player p)) return true;
        try {
            Object r = Reflect.call(api, M_ADMIN, (UUID) p.getUniqueId());
            return !(r instanceof Boolean b) || b;
        } catch (ReflectiveOperationException | RuntimeException e) {
            adminOk = false;
            warn(M_ADMIN, e);
            return true;
        }
    }

    @Override
    public String setting(String path, String def) {
        if (configOk) {
            try {
                Object v = Reflect.call(api, M_CONFIG, namespace, path);
                if (v != null) return String.valueOf(v);
            } catch (ReflectiveOperationException | RuntimeException e) {
                configOk = false;
                warn(M_CONFIG, e);
            }
        }
        return super.setting(path, def);
    }

    @Override
    public void audit(String actor, String action, String detail) {
        super.audit(actor, action, detail); // copie locale toujours conservee
        if (!auditOk) return;
        try {
            Reflect.call(api, M_AUDIT, namespace, clean(actor), clean(action), clean(detail));
        } catch (ReflectiveOperationException | RuntimeException e) {
            auditOk = false;
            warn(M_AUDIT, e);
        }
    }

    private void warn(String method, Exception e) {
        plugin.getLogger().log(Level.WARNING, "OneCore : methode '" + method + "' inutilisable, repli local pour cette capacite.", e);
    }
}
