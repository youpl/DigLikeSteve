package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

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
        m.source = Source.valueOf(s.getString("source", "VANILLA"));
        m.type = s.getString("type", "ZOMBIE");
        m.count = Math.max(1, s.getInt("count", 1));
        m.health = s.getDouble("health", 0);
        m.damageMultiplier = s.getDouble("damage-multiplier", 1.0);
        m.name = s.getString("name", "");
        m.effects = new ArrayList<>(s.getStringList("effects"));
        m.model = s.getString("model", "");
        m.spawnPoint = s.getString("spawn-point", "");
        m.bossBar = s.getBoolean("boss-bar", false);
        m.onDeath = Action.loadList(s, "on-death");
        return m;
    }

    public String describe() {
        return count + "x " + source + ":" + type;
    }
}
