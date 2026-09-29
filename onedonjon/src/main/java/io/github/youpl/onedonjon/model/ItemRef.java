package io.github.youpl.onedonjon.model;

import io.github.youpl.onedonjon.util.Limits;
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
        return new ItemRef(MobSpec.enumOr(Source.class, s.getString("source"), Source.VANILLA), MobSpec.clip(s.getString("id", "STONE"), 96),
                Limits.clamp(s.getInt("amount", 1), 1, Limits.MAX_ITEM_AMOUNT));
    }

    public String describe() {
        return source + " " + id + " x" + amount;
    }
}
