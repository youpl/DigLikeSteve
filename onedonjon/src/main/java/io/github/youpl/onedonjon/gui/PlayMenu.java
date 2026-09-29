package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Menu joueur interne (repli quand DeluxeMenus est absent) : liste des donjons jouables. */
public final class PlayMenu {

    private PlayMenu() {}

    public static Menu of(OneDonjonPlugin plugin, Player p) {
        return new ListMenu<Dungeon>(plugin, p, "<dark_gray>Donjons",
                () -> new ArrayList<>(plugin.storage().all().stream().filter(d -> d.enabled && d.problems().isEmpty()).toList()))
                .icon(d -> Items.of(Material.BOOK, "<gold>" + Msg.escape(d.displayName),
                        "<gray>Salles : <white>" + d.rooms.size(), "<gray>Joueurs : <white>" + d.minPlayers + "-" + d.maxPlayers,
                        Items.hint("creer / rejoindre le groupe", null)))
                .publicMenu()
                .onClick((d, e) -> {
                    p.closeInventory();
                    String err = plugin.runs().play(p, d);
                    if (err != null) Msg.error(p, err);
                });
    }
}
