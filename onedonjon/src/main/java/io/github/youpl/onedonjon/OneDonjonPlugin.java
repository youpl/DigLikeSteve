package io.github.youpl.onedonjon;

import io.github.youpl.onedonjon.bridge.CoreBridges;
import io.github.youpl.onedonjon.bridge.StandaloneCore;
import io.github.youpl.onedonjon.command.DonjonCommand;
import io.github.youpl.onedonjon.deluxe.DeluxeMenusBridge;
import io.github.youpl.onedonjon.gui.MenuListener;
import io.github.youpl.onedonjon.gui.Prompts;
import io.github.youpl.onedonjon.gui.Selections;
import io.github.youpl.onedonjon.integration.CitizensHook;
import io.github.youpl.onedonjon.integration.Hooks;
import io.github.youpl.onedonjon.run.ActionRunner;
import io.github.youpl.onedonjon.run.MobFactory;
import io.github.youpl.onedonjon.run.RunListener;
import io.github.youpl.onedonjon.run.RunManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class OneDonjonPlugin extends JavaPlugin {

    private StandaloneCore core;
    private DeluxeMenusBridge deluxe;
    private final java.util.Map<java.util.UUID, String> editing = new java.util.HashMap<>();
    private Hooks hooks;
    private CitizensHook citizens;
    private DungeonStorage storage;
    private ActionRunner actions;
    private MobFactory mobs;
    private RunManager runs;
    private Prompts prompts;
    private Selections selections;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = CoreBridges.create(this);
        hooks = new Hooks(getLogger());
        citizens = new CitizensHook(this);
        storage = new DungeonStorage(this);
        actions = new ActionRunner(this);
        mobs = new MobFactory(this);
        runs = new RunManager(this);
        prompts = new Prompts(this);
        selections = new Selections(this);

        storage.load();
        deluxe = new DeluxeMenusBridge(this);
        core.onReload(this::applyReload);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new RunListener(this), this);
        pm.registerEvents(prompts, this);
        pm.registerEvents(selections, this);

        var cmd = new DonjonCommand(this);
        getCommand("donjon").setExecutor(cmd);
        getCommand("donjon").setTabCompleter(cmd);

        // Les plugins soft-depend sont deja actives ici ; Citizens s'abonne a ses clics de PNJ.
        citizens.onClick(runs::onNpcClick);
        getLogger().info("Integrations : " + hooks.describe() + " | Core=" + core.name());
        getServer().getScheduler().runTask(this, deluxe::export);
    }

    @Override
    public void onDisable() {
        if (runs != null) runs.stopAll();
        if (core != null) core.close();
    }

    /** Recharge config + donjons. Les parties en cours sont arretees : leurs donnees seraient obsoletes. */
    public void reload() {
        reloadConfig();
        core.fireReload(); // declenche applyReload (enregistre aupres du pont)
    }

    private void applyReload() {
        runs.stopAll();
        storage.load();
        deluxe.export();
    }

    /** Enregistre une action d'administration dans le journal d'audit (OneCore et fichier local). */
    public void audit(org.bukkit.command.CommandSender who, String action, String detail) {
        core.audit(who == null ? "console" : who.getName(), action, detail);
    }

    private org.bukkit.command.CommandSender actor;
    private final java.util.Map<String, Long> lastEditAudit = new java.util.HashMap<>();

    /** Auteur de l'action en cours (menu clique, commande) pour l'audit ; null = systeme. */
    public org.bukkit.command.CommandSender actor() { return actor; }

    public void setActor(org.bukkit.command.CommandSender who) { this.actor = who; }

    /** Trace une sauvegarde de donjon, au plus une fois par 10 s et par auteur pour ne pas noyer le journal. */
    public void auditEdit(String dungeonId) {
        if (actor == null) return;
        String key = actor.getName() + "/" + dungeonId;
        long now = System.currentTimeMillis();
        Long last = lastEditAudit.get(key);
        if (last != null && now - last < 10_000) return;
        lastEditAudit.put(key, now);
        audit(actor, "dungeon.edit", dungeonId);
    }

    /** Donjon en cours d'edition par un joueur (les editions sont bloquees pendant une partie). */
    public void setEditing(org.bukkit.entity.Player p, String dungeonId) {
        if (dungeonId == null) editing.remove(p.getUniqueId());
        else editing.put(p.getUniqueId(), dungeonId);
    }

    public String editing(org.bukkit.entity.Player p) {
        return editing.get(p.getUniqueId());
    }

    public StandaloneCore core() { return core; }
    public DeluxeMenusBridge deluxe() { return deluxe; }
    public Hooks hooks() { return hooks; }
    public CitizensHook citizens() { return citizens; }
    public DungeonStorage storage() { return storage; }
    public ActionRunner actions() { return actions; }
    public MobFactory mobs() { return mobs; }
    public RunManager runs() { return runs; }
    public Prompts prompts() { return prompts; }
    public Selections selections() { return selections; }
}
