package io.github.youpl.onedonjon.model;

import org.bukkit.configuration.ConfigurationSection;

/** Piege actif pendant que sa salle est en cours. */
public final class Trap {

    public enum Type {
        /** Degats continus dans la zone. */ DAMAGE_ZONE,
        /** Effet de potion applique dans la zone. */ POTION_ZONE,
        /** Enflamme les joueurs dans la zone. */ FIRE_ZONE,
        /** Tire des fleches sur le joueur le plus proche. */ ARROW_TURRET,
        /** Foudre sur les joueurs de la zone. */ LIGHTNING,
        /** Projette les joueurs en l'air. */ LAUNCHER,
        /** Fait exploser une TNT a la position du joueur. */ TNT
    }

    public enum Mode {
        /** Se declenche a chaque intervalle tant qu'un joueur est dans la zone. */ CONTINUOUS,
        /** Se declenche une fois quand un joueur entre, puis recharge. */ ON_STEP
    }

    public String id = "trap";
    public Type type = Type.DAMAGE_ZONE;
    public Mode mode = Mode.CONTINUOUS;
    public Pos pos;
    public double radius = 3;
    /** Degats, force du lanceur, niveau d'effet... selon le type. */
    public double value = 2;
    public int intervalTicks = 20;
    /** "EFFET:niveau" pour POTION_ZONE. */
    public String effect = "SLOWNESS:1";

    public void save(ConfigurationSection s) {
        s.set("id", id);
        s.set("type", type.name());
        s.set("mode", mode.name());
        if (pos != null) pos.save(s.createSection("pos"));
        s.set("radius", radius);
        s.set("value", value);
        s.set("interval-ticks", intervalTicks);
        s.set("effect", effect);
    }

    public static Trap load(ConfigurationSection s) {
        Trap t = new Trap();
        t.id = s.getString("id", "trap");
        t.type = Type.valueOf(s.getString("type", "DAMAGE_ZONE"));
        t.mode = Mode.valueOf(s.getString("mode", "CONTINUOUS"));
        t.pos = Pos.load(s.getConfigurationSection("pos"));
        t.radius = s.getDouble("radius", 3);
        t.value = s.getDouble("value", 2);
        t.intervalTicks = Math.max(1, s.getInt("interval-ticks", 20));
        t.effect = s.getString("effect", "SLOWNESS:1");
        return t;
    }
}
