package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Action declenchee par un evenement du donjon (entree de salle, boss mort, recompense...).
 * <p>Valeurs : MESSAGE = texte MiniMessage, TITLE = "titre | sous-titre", COMMAND = commande console
 * ({player} remplace), TYPEWRITER = id d'entree Typewriter declenchee pour chaque joueur,
 * ITEM = "source id [quantite]" (VANILLA, MMOITEMS, NEXO), XP = niveaux, SOUND = nom de son.
 */
public record Action(Type type, String value) {

    public enum Type { MESSAGE, TITLE, COMMAND, TYPEWRITER, ITEM, XP, SOUND }

    public static void saveList(ConfigurationSection s, String key, List<Action> list) {
        List<String> out = new ArrayList<>();
        for (Action a : list) out.add(a.type + ":" + a.value);
        s.set(key, out);
    }

    public static List<Action> loadList(ConfigurationSection s, String key) {
        List<Action> out = new ArrayList<>();
        for (String raw : s.getStringList(key)) {
            int i = raw.indexOf(':');
            if (i < 0) continue;
            try {
                out.add(new Action(Type.valueOf(raw.substring(0, i)), raw.substring(i + 1)));
            } catch (IllegalArgumentException ignored) {
                // type inconnu : ligne ignoree
            }
        }
        return out;
    }

    public String describe() {
        return type + " " + value;
    }
}
