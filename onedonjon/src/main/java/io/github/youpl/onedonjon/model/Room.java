package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

public final class Room {

    public String id = "room";
    public String name = "Salle";
    /** Zone de la salle : l'entree d'un joueur l'active. */
    public Cuboid region;
    /** Zone verrouillee tant que la salle n'est pas terminee (porte / grille). */
    public Cuboid door;
    public String doorMaterial = "IRON_BARS";
    /** Point de reapparition (checkpoint) une fois la salle activee. */
    public Pos checkpoint;
    public List<SpawnPoint> spawnPoints = new ArrayList<>();
    public List<Wave> waves = new ArrayList<>();
    public MobSpec boss;
    public List<Trap> traps = new ArrayList<>();
    public List<Puzzle> puzzles = new ArrayList<>();
    public List<NpcSpec> npcs = new ArrayList<>();
    public List<Action> onEnter = new ArrayList<>();
    public List<Action> onClear = new ArrayList<>();

    public void save(ConfigurationSection s) {
        s.set("id", id);
        s.set("name", name);
        if (region != null) region.save(s.createSection("region"));
        if (door != null) door.save(s.createSection("door"));
        s.set("door-material", doorMaterial);
        if (checkpoint != null) checkpoint.save(s.createSection("checkpoint"));
        saveList(s, "spawn-points", spawnPoints, SpawnPoint::save);
        saveList(s, "waves", waves, Wave::save);
        if (boss != null) boss.save(s.createSection("boss"));
        saveList(s, "traps", traps, Trap::save);
        saveList(s, "puzzles", puzzles, Puzzle::save);
        saveList(s, "npcs", npcs, NpcSpec::save);
        Action.saveList(s, "on-enter", onEnter);
        Action.saveList(s, "on-clear", onClear);
    }

    public static Room load(ConfigurationSection s) {
        Room r = new Room();
        r.id = s.getString("id", "room");
        r.name = s.getString("name", "Salle");
        r.region = Cuboid.load(s.getConfigurationSection("region"));
        r.door = Cuboid.load(s.getConfigurationSection("door"));
        r.doorMaterial = s.getString("door-material", "IRON_BARS");
        r.checkpoint = Pos.load(s.getConfigurationSection("checkpoint"));
        loadList(s, "spawn-points", r.spawnPoints, SpawnPoint::load);
        loadList(s, "waves", r.waves, Wave::load);
        if (s.isConfigurationSection("boss")) r.boss = MobSpec.load(s.getConfigurationSection("boss"));
        loadList(s, "traps", r.traps, Trap::load);
        loadList(s, "puzzles", r.puzzles, Puzzle::load);
        loadList(s, "npcs", r.npcs, NpcSpec::load);
        r.onEnter = Action.loadList(s, "on-enter");
        r.onClear = Action.loadList(s, "on-clear");
        return r;
    }

    private interface Saver<T> { void save(T t, ConfigurationSection s); }

    private static <T> void saveList(ConfigurationSection s, String key, List<T> list, Saver<T> saver) {
        ConfigurationSection c = s.createSection(key);
        for (int i = 0; i < list.size(); i++) saver.save(list.get(i), c.createSection(String.valueOf(i)));
    }

    private static <T> void loadList(ConfigurationSection s, String key, List<T> out,
                                     java.util.function.Function<ConfigurationSection, T> loader) {
        ConfigurationSection c = s.getConfigurationSection(key);
        if (c == null) return;
        for (String k : c.getKeys(false)) out.add(loader.apply(c.getConfigurationSection(k)));
    }

    /** Problemes empechant un fonctionnement correct, pour affichage dans le GUI. */
    public List<String> problems() {
        List<String> p = new ArrayList<>();
        if (region == null) p.add("Aucune zone definie");
        boolean hasMobs = !waves.isEmpty() || boss != null;
        if (hasMobs && spawnPoints.isEmpty()) p.add("Des monstres mais aucun point d'apparition");
        for (Puzzle z : puzzles) {
            if (z.blocks.isEmpty()) p.add("Enigme '" + z.id + "' sans bloc");
            if (z.type == Puzzle.Type.KEY && z.key == null) p.add("Enigme '" + z.id + "' sans objet-cle");
        }
        for (Trap t : traps) if (t.pos == null) p.add("Piege '" + t.id + "' sans position");
        return p;
    }
}
