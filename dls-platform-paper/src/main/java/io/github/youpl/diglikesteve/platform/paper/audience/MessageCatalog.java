package io.github.youpl.diglikesteve.platform.paper.audience;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalogue de messages MiniMessage (DAT §17, {@code messages.yml}).
 *
 * <p>Le rendu est fait par Adventure natif : aucune couche paquet n'est nécessaire pour les
 * messages cliquables, les hover, les boss bars ou les titres (DAT §14).</p>
 */
public final class MessageCatalog {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> messages = new ConcurrentHashMap<>();
    private volatile String prefix = "";

    /** Recharge le catalogue depuis une configuration déjà lue. */
    public void load(FileConfiguration configuration) {
        messages.clear();
        prefix = configuration.getString("prefix", "");
        for (String key : configuration.getKeys(true)) {
            if (configuration.isString(key)) {
                messages.put(key, Objects.requireNonNull(configuration.getString(key)));
            }
        }
    }

    /** Message rendu, préfixe compris. Une clé absente est rendue telle quelle, pour diagnostic. */
    public Component render(String key, Object... placeholders) {
        String raw = messages.getOrDefault(key, "<red>[" + key + "]</red>");
        return MINI.deserialize(prefix + raw, resolvers(placeholders));
    }

    /** Message rendu sans préfixe, pour les boss bars et action bars. */
    public Component renderBare(String key, Object... placeholders) {
        String raw = messages.getOrDefault(key, "<red>[" + key + "]</red>");
        return MINI.deserialize(raw, resolvers(placeholders));
    }

    public Component renderRaw(String miniMessage, Object... placeholders) {
        return MINI.deserialize(miniMessage, resolvers(placeholders));
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }

    public String rawOrDefault(String key, String fallback) {
        return messages.getOrDefault(key, fallback);
    }

    /** Convertit une suite clé, valeur, clé, valeur en résolveurs MiniMessage. */
    private static TagResolver[] resolvers(Object... placeholders) {
        if (placeholders == null || placeholders.length < 2) {
            return new TagResolver[0];
        }
        List<TagResolver> resolvers = new ArrayList<>(placeholders.length / 2);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            resolvers.add(Placeholder.unparsed(normalizeTagName(String.valueOf(placeholders[i])),
                    String.valueOf(placeholders[i + 1])));
        }
        return resolvers.toArray(TagResolver[]::new);
    }

    /**
     * MiniMessage n'accepte qu'un nom de balise en {@code [a-z0-9_-]}. Une clé fournie en
     * camelCase ou avec un caractère exotique ferait échouer le rendu à l'exécution : on la
     * normalise plutôt que de laisser une commande lever une exception.
     */
    static String normalizeTagName(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 4);
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current >= 'A' && current <= 'Z') {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else if ((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9')
                    || current == '_' || current == '-') {
                builder.append(current);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "value" : builder.toString();
    }
}
