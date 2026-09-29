package io.github.youpl.onedonjon.gui;

import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.NpcSpec;
import io.github.youpl.onedonjon.model.Pos;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** PNJ Citizens d'une salle ; clic droit = actions (dialogues Typewriter, recompenses...). */
public final class NpcMenu extends Menu {

    private final Dungeon d;
    private final NpcSpec n;
    private final Supplier<Menu> back;

    private NpcMenu(OneDonjonPlugin plugin, Player p, Dungeon d, NpcSpec n, Supplier<Menu> back) {
        super(plugin, p);
        this.d = d;
        this.n = n;
        this.back = back;
    }

    public static Menu list(OneDonjonPlugin plugin, Player p, Dungeon d, Room r, Supplier<Menu> back) {
        ListMenu<NpcSpec>[] self = new ListMenu[1];
        self[0] = new ListMenu<NpcSpec>(plugin, p, "<dark_gray>PNJ", () -> r.npcs)
                .icon(n -> Items.of(Material.VILLAGER_SPAWN_EGG, "<gold>" + Msg.escape(n.name),
                        "<gray>" + (n.pos == null ? "<red>sans position" : n.pos.describe()),
                        Items.hint("editer", "supprimer (maj)")))
                .onClick((n, e) -> {
                    if (e.isRightClick() && e.isShiftClick()) {
                        r.npcs.remove(n);
                        plugin.storage().save(d);
                        self[0].refresh();
                    } else if (e.isLeftClick()) {
                        new NpcMenu(plugin, p, d, n, () -> self[0]).open();
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter un PNJ ici"), e -> {
                    NpcSpec n = new NpcSpec();
                    n.id = "npc" + (r.npcs.size() + 1);
                    n.pos = Pos.of(p.getLocation());
                    r.npcs.add(n);
                    plugin.storage().save(d);
                    new NpcMenu(plugin, p, d, n, () -> self[0]).open();
                })
                .back(back);
        return self[0];
    }

    private void save() {
        plugin.storage().save(d);
    }

    @Override protected String title() { return "<dark_gray>PNJ : " + Msg.escape(n.name); }
    @Override protected int rows() { return 3; }

    @Override
    protected void build() {
        text(10, Material.NAME_TAG, "<gold>Nom", () -> n.name, "Nom du PNJ :", v -> { n.name = v; save(); });
        text(11, Material.VILLAGER_SPAWN_EGG, "<gold>Type d'entite", () -> n.entityType, "Type d'entite (PLAYER, VILLAGER, ZOMBIE...) :", v -> {
            EntityType.valueOf(v.toUpperCase());
            n.entityType = v.toUpperCase();
            save();
        });
        set(12, Items.of(Material.COMPASS, "<gold>Position", "<gray>" + (n.pos == null ? "<red>non definie" : n.pos.describe()),
                Items.hint("definir ici", null)), e -> {
            n.pos = Pos.of(player.getLocation());
            save();
            refresh();
        });
        text(13, Material.ARMOR_STAND, "<gold>Modele ModelEngine", () -> n.model, "Id du modele ModelEngine :", v -> { n.model = v; save(); },
                plugin.hooks().modelEngine() ? "<green>ModelEngine detecte" : "<red>ModelEngine absent");
        set(14, Items.of(Material.WRITABLE_BOOK, "<gold>Actions au clic droit", "<gray>" + n.onClick.size() + " action(s)",
                "<dark_gray>Ex. TYPEWRITER pour lancer un dialogue.", Items.hint("editer", null)),
                e -> ActionsMenu.of(plugin, player, "<dark_gray>Clic sur le PNJ", n.onClick, this::save, () -> this).open());
        fillBottom();
        backButton(back);
    }
}
