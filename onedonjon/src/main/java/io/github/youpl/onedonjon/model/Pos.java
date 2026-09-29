package io.github.youpl.onedonjon.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Position serialisable (monde par nom, pour survivre au rechargement des mondes). */
public record Pos(String world, double x, double y, double z, float yaw, float pitch) {

    public static Pos of(Location l) {
        return new Pos(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
        s.set("pitch", (double) pitch);
    }

    public static Pos load(ConfigurationSection s) {
        if (s == null) return null;
        return new Pos(s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public String describe() {
        return "%s %.0f %.0f %.0f".formatted(world, x, y, z);
    }
}
