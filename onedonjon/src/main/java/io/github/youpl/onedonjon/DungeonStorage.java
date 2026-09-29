package io.github.youpl.onedonjon;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import io.github.youpl.onedonjon.model.Dungeon;
import org.bukkit.configuration.file.YamlConfiguration;

/** Un fichier YAML par donjon dans plugins/OneDonjon/dungeons/. */
public final class DungeonStorage {

    private final OneDonjonPlugin plugin;
    private final File dir;
    private final Map<String, Dungeon> dungeons = new TreeMap<>();

    public DungeonStorage(OneDonjonPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "dungeons");
    }

    public void load() {
        dungeons.clear();
        if (!dir.isDirectory() && !dir.mkdirs()) plugin.getLogger().warning("Dossier donjons non cree : " + dir);
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                Dungeon d = Dungeon.fromYaml(YamlConfiguration.loadConfiguration(f));
                if (!f.getName().equals(d.id + ".yml")) {
                    plugin.getLogger().severe("Donjon ignore : le fichier " + f.getName() + " ne correspond pas a l'id '" + d.id + "'");
                    continue;
                }
                dungeons.put(d.id, d);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Donjon illisible : " + f.getName(), e);
            }
        }
        plugin.getLogger().info(dungeons.size() + " donjon(s) charge(s).");
    }

    public void save(Dungeon d) {
        if (!validId(d.id)) throw new IllegalStateException("identifiant invalide : " + d.id);
        plugin.auditEdit(d.id);
        try {
            d.toYaml().save(new File(dir, d.id + ".yml"));
            if (plugin.deluxe() != null) plugin.deluxe().requestExport();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Sauvegarde impossible : " + d.id, e);
        }
    }

    public Dungeon get(String id) {
        return dungeons.get(id.toLowerCase());
    }

    public Collection<Dungeon> all() {
        return dungeons.values();
    }

    public Dungeon create(String id) {
        Dungeon d = new Dungeon(id.toLowerCase());
        d.displayName = id;
        dungeons.put(d.id, d);
        plugin.audit(plugin.actor(), "dungeon.create", d.id);
        save(d);
        return d;
    }

    public void delete(Dungeon d) {
        var run = plugin.runs().of(d.id);
        if (run != null) run.end(false, "Donjon supprime.");
        plugin.audit(plugin.actor(), "dungeon.delete", d.id);
        dungeons.remove(d.id);
        File f = new File(dir, d.id + ".yml");
        if (f.exists() && !f.delete()) plugin.getLogger().warning("Suppression impossible : " + f);
        if (plugin.deluxe() != null) plugin.deluxe().requestExport();
    }

    public static boolean validId(String id) {
        return id.matches("[a-zA-Z0-9_-]{1,32}");
    }
}
