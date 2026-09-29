package io.github.youpl.onedonjon.gui;

import java.util.List;
import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Action;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Edition d'une liste d'actions (messages, commandes, Typewriter, objets, xp, sons). */
public final class ActionsMenu {

    private ActionsMenu() {}

    public static Menu of(OneDonjonPlugin plugin, Player p, String title, List<Action> list, Runnable save,
                          Supplier<Menu> back) {
        ListMenu<Action>[] self = new ListMenu[1];
        self[0] = new ListMenu<Action>(plugin, p, title, () -> list)
                .icon(a -> Items.of(icon(a.type()), "<gold>" + a.type(),
                        "<white>" + Msg.escape(a.value()),
                        Items.hint("modifier", "supprimer")))
                .onClick((a, e) -> {
                    int i = list.indexOf(a);
                    if (e.isRightClick()) {
                        list.remove(i);
                        save.run();
                        self[0].refresh();
                    } else if (a.type() == Action.Type.ITEM) {
                        pickItem(plugin, p, v -> {
                            list.set(i, new Action(Action.Type.ITEM, v));
                            save.run();
                        }, self[0]);
                    } else {
                        plugin.prompts().ask(p, question(a.type()), v -> {
                            list.set(i, new Action(a.type(), v));
                            save.run();
                            self[0].open();
                        }, self[0]::open);
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter une action"), e ->
                        ListMenu.pick(plugin, p, "Type d'action", List.of(Action.Type.values()),
                                t -> Items.of(icon(t), "<gold>" + t, "<gray>" + question(t)),
                                t -> add(plugin, p, t, list, save, self[0]), () -> self[0]).open())
                .back(back);
        return self[0];
    }

    private static void add(OneDonjonPlugin plugin, Player p, Action.Type t, List<Action> list, Runnable save,
                            ListMenu<Action> parent) {
        if (t == Action.Type.ITEM) {
            pickItem(plugin, p, v -> {
                list.add(new Action(t, v));
                save.run();
            }, parent);
            return;
        }
        plugin.prompts().ask(p, question(t), v -> {
            list.add(new Action(t, v));
            save.run();
            parent.open();
        }, parent::open);
    }

    private static void pickItem(OneDonjonPlugin plugin, Player p, java.util.function.Consumer<String> done,
                                 ListMenu<Action> parent) {
        ItemPicker.pick(plugin, p, ref -> {
            done.accept(ref.source() + " " + ref.id() + " " + ref.amount());
            parent.open();
        }, () -> parent);
    }

    static Material icon(Action.Type t) {
        return switch (t) {
            case MESSAGE -> Material.PAPER;
            case TITLE -> Material.NAME_TAG;
            case COMMAND -> Material.COMMAND_BLOCK;
            case TYPEWRITER -> Material.WRITABLE_BOOK;
            case ITEM -> Material.CHEST;
            case XP -> Material.EXPERIENCE_BOTTLE;
            case SOUND -> Material.NOTE_BLOCK;
        };
    }

    static String question(Action.Type t) {
        return switch (t) {
            case MESSAGE -> "Texte du message (MiniMessage, ex. <red>Attention !</red>) :";
            case TITLE -> "Titre | sous-titre (ex. Bienvenue | Bonne chance) :";
            case COMMAND -> "Commande console, sans / ({player} = joueur) :";
            case TYPEWRITER -> "Id de l'entree Typewriter a declencher :";
            case ITEM -> "Objet a donner :";
            case XP -> "Niveaux d'experience a donner (nombre) :";
            case SOUND -> "Nom du son (ex. entity.player.levelup) :";
        };
    }
}
