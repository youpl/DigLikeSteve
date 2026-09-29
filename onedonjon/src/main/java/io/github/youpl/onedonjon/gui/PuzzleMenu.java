package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.Puzzle;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class PuzzleMenu extends Menu {

    private final Dungeon d;
    private final Puzzle z;
    private final Supplier<Menu> back;

    private PuzzleMenu(OneDonjonPlugin plugin, Player p, Dungeon d, Puzzle z, Supplier<Menu> back) {
        super(plugin, p);
        this.d = d;
        this.z = z;
        this.back = back;
    }

    public static Menu list(OneDonjonPlugin plugin, Player p, Dungeon d, Room r, Supplier<Menu> back) {
        ListMenu<Puzzle>[] self = new ListMenu[1];
        self[0] = new ListMenu<Puzzle>(plugin, p, "<dark_gray>Enigmes", () -> r.puzzles)
                .icon(z -> Items.of(icon(z.type), "<gold>" + z.id + " <dark_gray>(" + z.type + ")",
                        "<gray>Blocs : <white>" + z.blocks.size() + "<gray>  " + (z.required ? "obligatoire" : "facultative"),
                        Items.hint("editer", "supprimer (maj)")))
                .onClick((z, e) -> {
                    if (e.isRightClick() && e.isShiftClick()) {
                        r.puzzles.remove(z);
                        plugin.storage().save(d);
                        self[0].refresh();
                    } else if (e.isLeftClick()) {
                        new PuzzleMenu(plugin, p, d, z, () -> self[0]).open();
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter une enigme"), e -> {
                    Puzzle z = new Puzzle();
                    int n = r.puzzles.size() + 1;
                    while (true) {
                        final String id = "puzzle" + n;
                        if (r.puzzles.stream().noneMatch(o -> o.id.equals(id))) { z.id = id; break; }
                        n++;
                    }
                    r.puzzles.add(z);
                    plugin.storage().save(d);
                    new PuzzleMenu(plugin, p, d, z, () -> self[0]).open();
                })
                .back(back);
        return self[0];
    }

    static Material icon(Puzzle.Type t) {
        return switch (t) {
            case SEQUENCE -> Material.LEVER;
            case KEY -> Material.TRIPWIRE_HOOK;
            case PLATES -> Material.STONE_PRESSURE_PLATE;
        };
    }

    private void save() {
        plugin.storage().save(d);
    }

    @Override protected String title() { return "<dark_gray>Enigme : " + Msg.escape(z.id); }
    @Override protected int rows() { return 4; }

    @Override
    protected void build() {
        cycle(10, icon(z.type), "<gold>Type", Puzzle.Type.values(), () -> z.type, v -> { z.type = v; save(); },
                "<dark_gray>" + describe(z.type));

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + z.blocks.size() + " bloc(s), dans l'ordre :");
        int i = 1;
        for (String b : z.blocks) lore.add("<white>" + i++ + ". " + b.replace(',', ' '));
        lore.add(Items.hint("ajouter le bloc vise", "retirer le dernier"));
        set(11, Items.of(Material.SPYGLASS, "<gold>Blocs", lore), e -> {
            if (e.isRightClick()) {
                if (!z.blocks.isEmpty()) z.blocks.remove(z.blocks.size() - 1);
            } else {
                Block b = player.getTargetBlockExact(8);
                if (b == null) {
                    Msg.error(player, "Visez un bloc a moins de 8 blocs.");
                    return;
                }
                z.blocks.add(Puzzle.blockKey(b));
            }
            save();
            refresh();
        });

        if (z.type == Puzzle.Type.KEY) {
            set(12, Items.of(Material.TRIPWIRE_HOOK, "<gold>Objet-cle", "<gray>Actuel : <white>" + (z.key == null ? "<red>non defini" : z.key.describe()),
                    "<dark_gray>Clic droit sur le 1er bloc avec cet objet.", Items.hint("choisir", null)),
                    e -> ItemPicker.pick(plugin, player, ref -> { z.key = ref; save(); open(); }, () -> this));
        }
        toggle(13, "<gold>Obligatoire", () -> z.required, v -> { z.required = v; save(); },
                "<dark_gray>Doit etre resolue pour terminer la salle");
        if (z.type == Puzzle.Type.SEQUENCE) {
            number(14, Material.IRON_SWORD, "<gold>Degats si erreur", () -> z.wrongDamage, 0.5, 0, 100, v -> { z.wrongDamage = v; save(); });
        }
        text(15, Material.NAME_TAG, "<gold>Identifiant", () -> z.id, "Identifiant de l'enigme :", v -> { z.id = v.replace(' ', '_'); save(); });
        set(16, Items.of(Material.WRITABLE_BOOK, "<gold>Actions a la resolution", "<gray>" + z.onSolve.size() + " action(s)",
                Items.hint("editer", null)), e -> ActionsMenu.of(plugin, player, "<dark_gray>Enigme resolue", z.onSolve, this::save, () -> this).open());
        fillBottom();
        backButton(back);
    }

    private static String describe(Puzzle.Type t) {
        return switch (t) {
            case SEQUENCE -> "Cliquer les blocs dans l'ordre";
            case KEY -> "Clic droit avec l'objet-cle sur le bloc";
            case PLATES -> "Occuper tous les blocs en meme temps";
        };
    }
}
