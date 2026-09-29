package io.github.youpl.onedonjon.model;

import org.bukkit.configuration.ConfigurationSection;

/** Reference a un objet : vanilla, MMOItems ("TYPE:ID") ou Nexo (id). */
public record ItemRef(Source source, String id, int amount) {

    public enum Source { VANILLA, MMOITEMS, NEXO }

    public void save(ConfigurationSection s) {
        s.set("source", source.name());
        s.set("id", id);
        s.set("amount", amount);
    }

    public static ItemRef load(ConfigurationSection s) {
        if (s == null) return null;
        return new ItemRef(Source.valueOf(s.getString("source", "VANILLA")), s.getString("id", "STONE"),
                Math.max(1, s.getInt("amount", 1)));
    }

    public String describe() {
        return source + " " + id + " x" + amount;
    }
}
