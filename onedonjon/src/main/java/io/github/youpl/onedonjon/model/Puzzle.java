package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

public final class Puzzle {

    public enum Type {
        /** Cliquer sur les blocs dans l'ordre (boutons, leviers, n'importe quel bloc). */ SEQUENCE,
        /** Faire un clic droit avec l'objet-cle sur le bloc cible. */ KEY,
        /** Tous les blocs (plaques) doivent etre occupes en meme temps. */ PLATES
    }

    public String id = "puzzle";
    public Type type = Type.SEQUENCE;
    /** Blocs concernes, "monde,x,y,z". */
    public List<String> blocks = new ArrayList<>();
    public ItemRef key;
    /** Le puzzle doit etre resolu pour terminer la salle. */
    public boolean required = true;
    /** Degats infliges sur une mauvaise reponse (SEQUENCE). */
    public double wrongDamage = 0;
    public List<Action> onSolve = new ArrayList<>();

    public void save(ConfigurationSection s) {
        s.set("id", id);
        s.set("type", type.name());
        s.set("blocks", blocks);
        if (key != null) key.save(s.createSection("key"));
        s.set("required", required);
        s.set("wrong-damage", wrongDamage);
        Action.saveList(s, "on-solve", onSolve);
    }

    public static Puzzle load(ConfigurationSection s) {
        Puzzle p = new Puzzle();
        p.id = s.getString("id", "puzzle");
        p.type = Type.valueOf(s.getString("type", "SEQUENCE"));
        p.blocks = new ArrayList<>(s.getStringList("blocks"));
        p.key = ItemRef.load(s.getConfigurationSection("key"));
        p.required = s.getBoolean("required", true);
        p.wrongDamage = s.getDouble("wrong-damage", 0);
        p.onSolve = Action.loadList(s, "on-solve");
        return p;
    }

    public static String blockKey(org.bukkit.block.Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }
}
