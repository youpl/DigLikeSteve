package io.github.youpl.onedonjon.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#f5a623:#d0021b><bold>OneDonjon</bold></gradient> <dark_gray>»</dark_gray> ";

    private Msg() {}

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** Composant sans italique par defaut (noms et lores d'items). */
    public static Component plain(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(mm(PREFIX + "<gray>" + s));
    }

    public static void error(CommandSender to, String s) {
        to.sendMessage(mm(PREFIX + "<red>" + s));
    }

    /** Echappe les balises MiniMessage d'une saisie utilisateur affichee telle quelle. */
    public static String escape(String s) {
        return MM.escapeTags(s);
    }
}
