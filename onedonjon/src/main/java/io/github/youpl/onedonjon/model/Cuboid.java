package io.github.youpl.onedonjon.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/** Zone cuboide alignee sur les blocs, bornes incluses. */
public record Cuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Cuboid of(Location a, Location b) {
        return new Cuboid(a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
                && l.getX() >= minX && l.getX() < maxX + 1
                && l.getY() >= minY && l.getY() < maxY + 1
                && l.getZ() >= minZ && l.getZ() < maxZ + 1;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("min", minX + "," + minY + "," + minZ);
        s.set("max", maxX + "," + maxY + "," + maxZ);
    }

    public static Cuboid load(ConfigurationSection s) {
        if (s == null) return null;
        int[] mn = parse(s.getString("min", "0,0,0"));
        int[] mx = parse(s.getString("max", "0,0,0"));
        return new Cuboid(s.getString("world", "world"), mn[0], mn[1], mn[2], mx[0], mx[1], mx[2]);
    }

    private static int[] parse(String v) {
        String[] p = v.split(",");
        return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
    }

    public String describe() {
        return "%s [%d %d %d] -> [%d %d %d] (%d blocs)".formatted(world, minX, minY, minZ, maxX, maxY, maxZ, volume());
    }
}
