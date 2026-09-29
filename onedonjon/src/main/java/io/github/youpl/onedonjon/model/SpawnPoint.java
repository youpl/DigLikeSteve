package io.github.youpl.onedonjon.model;

import org.bukkit.configuration.ConfigurationSection;

public record SpawnPoint(String id, Pos pos) {

    public void save(ConfigurationSection s) {
        s.set("id", id);
        pos.save(s.createSection("pos"));
    }

    public static SpawnPoint load(ConfigurationSection s) {
        return new SpawnPoint(s.getString("id"), Pos.load(s.getConfigurationSection("pos")));
    }
}
