package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

import io.github.youpl.onedonjon.util.Limits;
import org.bukkit.configuration.ConfigurationSection;

/** Description complete d'un groupe de monstres identiques. */
public final class MobSpec {

    public enum Source { VANILLA, MYTHICMOBS }

    public Source source = Source.VANILLA;
    /** EntityType (vanilla) ou nom interne MythicMobs. */
    public String type = "ZOMBIE";
    public int count = 1;
    /** Points de vie maximum ; 0 = valeur par defaut du monstre. */
    public double health = 0;
    /** Multiplicateur des degats infliges aux joueurs. */
    public double damageMultiplier = 1.0;
    /** Nom affiche (MiniMessage), vide = par defaut. */
    public String name = "";
    /** Effets permanents "EFFET:niveau". */
    public List<String> effects = new ArrayList<>();
    /** Modele ModelEngine applique au monstre, vide = aucun. */
    public String model = "";
    /** Id du point d'apparition, vide = aleatoire parmi ceux de la salle. */
    public String spawnPoint = "";
    /** Barre de vie affichee (boss). */
    public boolean bossBar = false;
    public List<Action> onDeath = new ArrayList<>();

    public void save(ConfigurationSection s) {
        s.set("source", source.name());
        s.set("type", type);
        s.set("count", count);
        s.set("health", health);
        s.set("damage-multiplier", damageMultiplier);
        s.set("name", name);
        s.set("effects", effects);
        s.set("model", model);
        s.set("spawn-point", spawnPoint);
        s.set("boss-bar", bossBar);
        Action.saveList(s, "on-death", onDeath);
    }

    public static MobSpec load(ConfigurationSection s) {
        MobSpec m = new MobSpec();
        if (s == null) return m;
        m.source = enumOr(Source.class, s.getString("source"), Source.VANILLA);
        m.type = clip(s.getString("type", "ZOMBIE"), 64);
        m.count = Limits.clamp(s.getInt("count", 1), 1, Limits.MAX_MOBS_PER_SPEC);
        m.health = Limits.clamp(s.getDouble("health", 0), 0, Limits.MAX_HEALTH);
        m.damageMultiplier = Limits.clamp(s.getDouble("damage-multiplier", 1.0), 0, Limits.MAX_DAMAGE_MULT);
        m.name = clip(s.getString("name", ""), Limits.MAX_TEXT);
        m.effects = new ArrayList<>(s.getStringList("effects").stream().limit(Limits.MAX_LIST).toList());
        m.model = clip(s.getString("model", ""), 128);
        m.spawnPoint = clip(s.getString("spawn-point", ""), 64);
        m.bossBar = s.getBoolean("boss-bar", false);
        m.onDeath = Action.loadList(s, "on-death");
        return m;
    }

    static <E extends Enum<E>> E enumOr(Class<E> c, String v, E def) {
        if (v == null) return def;
        try {
            return Enum.valueOf(c, v.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    static String clip(String v, int max) {
        return v == null ? "" : v.length() > max ? v.substring(0, max) : v;
    }

    public String describe() {
        return count + "x " + source + ":" + type;
    }
}
