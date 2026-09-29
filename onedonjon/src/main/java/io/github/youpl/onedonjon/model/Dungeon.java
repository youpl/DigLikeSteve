package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Dungeon {

    public final String id;
    public String displayName;
    public boolean enabled = true;
    public Pos start;
    public Pos exit;
    public int lives = 3;
    /** 0 = pas de limite. */
    public int timeLimitSeconds = 0;
    public int minPlayers = 1;
    public int maxPlayers = 4;
    public List<Room> rooms = new ArrayList<>();
    public List<Action> onStart = new ArrayList<>();
    /** Recompenses distribuees a chaque joueur survivant a la victoire. */
    public List<Action> rewards = new ArrayList<>();

    public Dungeon(String id) {
        this.id = id;
        this.displayName = id;
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("id", id);
        y.set("display-name", displayName);
        y.set("enabled", enabled);
        if (start != null) start.save(y.createSection("start"));
        if (exit != null) exit.save(y.createSection("exit"));
        y.set("lives", lives);
        y.set("time-limit-seconds", timeLimitSeconds);
        y.set("min-players", minPlayers);
        y.set("max-players", maxPlayers);
        Action.saveList(y, "on-start", onStart);
        Action.saveList(y, "rewards", rewards);
        ConfigurationSection r = y.createSection("rooms");
        for (int i = 0; i < rooms.size(); i++) rooms.get(i).save(r.createSection(String.valueOf(i)));
        return y;
    }

    public static Dungeon fromYaml(YamlConfiguration y) {
        Dungeon d = new Dungeon(y.getString("id"));
        d.displayName = y.getString("display-name", d.id);
        d.enabled = y.getBoolean("enabled", true);
        d.start = Pos.load(y.getConfigurationSection("start"));
        d.exit = Pos.load(y.getConfigurationSection("exit"));
        d.lives = y.getInt("lives", 3);
        d.timeLimitSeconds = y.getInt("time-limit-seconds", 0);
        d.minPlayers = y.getInt("min-players", 1);
        d.maxPlayers = y.getInt("max-players", 4);
        d.onStart = Action.loadList(y, "on-start");
        d.rewards = Action.loadList(y, "rewards");
        ConfigurationSection r = y.getConfigurationSection("rooms");
        if (r != null) for (String k : r.getKeys(false)) d.rooms.add(Room.load(r.getConfigurationSection(k)));
        return d;
    }

    public List<String> problems() {
        List<String> p = new ArrayList<>();
        if (start == null) p.add("Point de depart non defini");
        if (rooms.isEmpty()) p.add("Aucune salle");
        for (Room r : rooms) for (String s : r.problems()) p.add(r.name + " : " + s);
        return p;
    }
}
