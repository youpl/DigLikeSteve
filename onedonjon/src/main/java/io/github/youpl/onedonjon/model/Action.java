package io.github.youpl.onedonjon.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Action declenchee par un evenement du donjon (entree de salle, boss mort, recompense...).
 * <p>Valeurs : MESSAGE = texte MiniMessage, TITLE = "titre | sous-titre", COMMAND = commande console
 * ({player} remplace), TYPEWRITER = id d'entree Typewriter declenchee pour chaque joueur,
 * ITEM = "source id [quantite]" (VANILLA, MMOITEMS, NEXO), XP = niveaux, SOUND = nom de son.
 */
public record Action(Type type, String value) {

    public enum Type { MESSAGE, TITLE, COMMAND, TYPEWRITER, ITEM, XP, SOUND }

    /** Id d'entree Typewriter : jamais d'espace ni de caractere de controle (injection dans la commande). */
    private static final Pattern ENTRY = Pattern.compile("[A-Za-z0-9_.:/\\-]{1,128}");
    private static final Pattern SOUND = Pattern.compile("[a-z0-9_.:/\\-]{1,128}");
    private static final Pattern ITEM = Pattern.compile("[A-Za-z0-9_:.\\-]{1,96}");

    /** @return un message d'erreur si la valeur est invalide pour ce type, sinon null. */
    public static String validate(Type type, String value) {
        if (value == null || value.isBlank()) return "valeur vide";
        if (value.length() > 256) return "valeur trop longue (256 max)";
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return "caracteres de controle interdits";
        switch (type) {
            case TYPEWRITER -> {
                if (!ENTRY.matcher(value.trim()).matches()) return "id d'entree invalide (lettres, chiffres, _ . : / -)";
            }
            case SOUND -> {
                if (!SOUND.matcher(value.trim()).matches()) return "nom de son invalide";
            }
            case XP -> {
                try {
                    int n = Integer.parseInt(value.trim());
                    if (n < -1000 || n > 1000) return "niveaux hors limites (-1000..1000)";
                } catch (NumberFormatException e) {
                    return "nombre entier attendu";
                }
            }
            case ITEM -> {
                String[] p = value.trim().split("\\s+");
                if (p.length < 2 || p.length > 3) return "format : source id [quantite]";
                try {
                    ItemRef.Source.valueOf(p[0].toUpperCase());
                } catch (IllegalArgumentException e) {
                    return "source inconnue (VANILLA, MMOITEMS, NEXO)";
                }
                if (!ITEM.matcher(p[1]).matches()) return "id d'objet invalide";
                if (p.length == 3) {
                    try {
                        int n = Integer.parseInt(p[2]);
                        if (n < 1 || n > 2304) return "quantite hors limites (1..2304)";
                    } catch (NumberFormatException e) {
                        return "quantite invalide";
                    }
                }
            }
            default -> { }
        }
        return null;
    }

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
            if (out.size() >= 200) break;
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
