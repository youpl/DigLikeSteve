package io.github.youpl.onedonjon;

import io.github.youpl.onedonjon.command.DonjonCommand;
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
        hooks = new Hooks(getLogger());
        citizens = new CitizensHook(this);
        storage = new DungeonStorage(this);
        actions = new ActionRunner(this);
        mobs = new MobFactory(this);
        runs = new RunManager(this);
        prompts = new Prompts(this);
        selections = new Selections(this);

        storage.load();

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
        getLogger().info("Integrations : " + hooks.describe());
    }

    @Override
    public void onDisable() {
        if (runs != null) runs.stopAll();
    }

    public void reload() {
        reloadConfig();
        storage.load();
    }

    public Hooks hooks() { return hooks; }
    public CitizensHook citizens() { return citizens; }
    public DungeonStorage storage() { return storage; }
    public ActionRunner actions() { return actions; }
    public MobFactory mobs() { return mobs; }
    public RunManager runs() { return runs; }
    public Prompts prompts() { return prompts; }
    public Selections selections() { return selections; }
}
