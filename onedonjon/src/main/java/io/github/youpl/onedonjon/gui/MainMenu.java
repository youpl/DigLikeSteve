package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;
import java.util.List;

import io.github.youpl.onedonjon.DungeonStorage;
import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Point d'entree de l'editeur : liste des donjons. */
public final class MainMenu {

    private MainMenu() {}

    public static Menu of(OneDonjonPlugin plugin, Player p) {
        plugin.setEditing(p, null);
        ListMenu<Dungeon>[] self = new ListMenu[1];
        self[0] = new ListMenu<Dungeon>(plugin, p, "<dark_gray>OneDonjon - Donjons", () -> new ArrayList<>(plugin.storage().all()))
                .icon(d -> {
                    List<String> lore = new ArrayList<>();
                    lore.add("<gray>Salles : <white>" + d.rooms.size());
                    lore.add(d.enabled ? "<green>Actif" : "<red>Desactive");
                    List<String> pb = d.problems();
                    if (!pb.isEmpty()) {
                        lore.add("<red>" + pb.size() + " probleme(s) :");
                        pb.stream().limit(4).forEach(s -> lore.add("<red> - " + Msg.escape(s)));
                    }
                    lore.add(Items.hint("editer", null));
                    return Items.of(d.enabled ? Material.BOOK : Material.WRITTEN_BOOK, "<gold>" + Msg.escape(d.displayName)
                            + " <dark_gray>(" + d.id + ")", lore);
                })
                .onClick((d, e) -> new DungeonMenu(plugin, p, d).open())
                .button(() -> Items.of(Material.LIME_DYE, "<green>Nouveau donjon"), e ->
                        plugin.prompts().ask(p, "Identifiant du nouveau donjon (lettres, chiffres, - et _) :", v -> {
                            if (!DungeonStorage.validId(v)) throw new IllegalArgumentException("identifiant invalide");
                            if (plugin.storage().get(v) != null) throw new IllegalArgumentException("existe deja");
                            new DungeonMenu(plugin, p, plugin.storage().create(v)).open();
                        }, self[0]::open))
                .button(() -> Items.of(Material.BLAZE_ROD, "<gold>Baguette de selection",
                        "<gray>Pour delimiter salles et portes.", Items.hint("recevoir", null)), e -> {
                    p.getInventory().addItem(plugin.selections().wand());
                    Msg.send(p, "Baguette donnee : clic gauche = coin 1, clic droit = coin 2.");
                })
                .button(() -> {
                    var h = plugin.hooks();
                    return Items.of(Material.COMPARATOR, "<aqua>Integrations",
                            state("MythicMobs", h.mythic()), state("MMOItems", h.mmoItems()), state("Nexo", h.nexo()),
                            state("ModelEngine", h.modelEngine()), state("Citizens", h.citizens()),
                            state("Typewriter", h.typewriter()));
                }, null);
        return self[0];
    }

    private static String state(String name, boolean on) {
        return (on ? "<green>+ " : "<red>- ") + name;
    }
}
