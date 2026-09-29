package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;
import java.util.List;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.Pos;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class DungeonMenu extends Menu {

    private final Dungeon d;

    public DungeonMenu(OneDonjonPlugin plugin, Player p, Dungeon d) {
        super(plugin, p);
        this.d = d;
    }

    private void save() {
        plugin.storage().save(d);
    }

    @Override protected String title() { return "<dark_gray>Donjon : " + d.id; }
    @Override protected int rows() { return 4; }

    @Override
    protected void build() {
        text(10, Material.NAME_TAG, "<gold>Nom affiche", () -> d.displayName, "Nouveau nom (MiniMessage autorise) :",
                v -> { d.displayName = v; save(); });
        toggle(11, "<gold>Donjon actif", () -> d.enabled, v -> { d.enabled = v; save(); });
        pos(12, Material.LIME_BED, "<green>Point de depart", d.start, p -> d.start = p);
        pos(13, Material.OAK_DOOR, "<red>Point de sortie", d.exit, p -> d.exit = p);
        number(14, Material.TOTEM_OF_UNDYING, "<gold>Vies par joueur", () -> (double) d.lives, 1, 1, 20,
                v -> { d.lives = v.intValue(); save(); });
        number(15, Material.CLOCK, "<gold>Limite de temps (s)", () -> (double) d.timeLimitSeconds, 30, 0, 7200,
                v -> { d.timeLimitSeconds = v.intValue(); save(); }, "<dark_gray>0 = illimite");
        number(16, Material.PLAYER_HEAD, "<gold>Joueurs min", () -> (double) d.minPlayers, 1, 1, 50,
                v -> { d.minPlayers = v.intValue(); d.maxPlayers = Math.max(d.maxPlayers, d.minPlayers); save(); });
        number(19, Material.PLAYER_HEAD, "<gold>Joueurs max", () -> (double) d.maxPlayers, 1, 1, 50,
                v -> { d.maxPlayers = Math.max(v.intValue(), d.minPlayers); save(); });

        set(20, Items.of(Material.WRITABLE_BOOK, "<gold>Actions au demarrage", "<gray>" + d.onStart.size() + " action(s)",
                Items.hint("editer", null)), e -> ActionsMenu.of(plugin, player, "<dark_gray>Demarrage", d.onStart, this::save, () -> this).open());
        set(21, Items.of(Material.CHEST, "<gold>Recompenses de victoire", "<gray>" + d.rewards.size() + " action(s)",
                "<dark_gray>Donnees a chaque survivant.", Items.hint("editer", null)),
                e -> ActionsMenu.of(plugin, player, "<dark_gray>Recompenses", d.rewards, this::save, () -> this).open());

        set(22, Items.of(Material.IRON_BARS, "<gold>Salles", "<gray>" + d.rooms.size() + " salle(s)", Items.hint("editer", null)),
                e -> rooms().open());

        List<String> pb = d.problems();
        List<String> lore = new ArrayList<>();
        if (pb.isEmpty()) lore.add("<green>Aucun probleme : pret a jouer.");
        else pb.stream().limit(10).forEach(s -> lore.add("<red>- " + Msg.escape(s)));
        set(23, Items.of(pb.isEmpty() ? Material.EMERALD : Material.REDSTONE, "<gold>Verification", lore), null);

        set(24, Items.of(Material.DIAMOND_SWORD, "<green>Tester (solo)", "<gray>Lance le donjon immediatement.",
                Items.hint("lancer", null)), e -> {
            player.closeInventory();
            String err = plugin.runs().start(player, d);
            if (err != null) Msg.error(player, err);
        });
        set(25, Items.of(Material.TNT, "<red>Supprimer le donjon", "<dark_gray>Maj + clic gauche pour confirmer"), e -> {
            if (!e.isShiftClick()) return;
            plugin.storage().delete(d);
            MainMenu.of(plugin, player).open();
        });
        fillBottom();
        backButton(() -> MainMenu.of(plugin, player));
    }

    private void pos(int slot, Material mat, String label, Pos cur, java.util.function.Consumer<Pos> setter) {
        set(slot, Items.of(mat, label, "<gray>Actuel : <white>" + (cur == null ? "<red>non defini" : cur.describe()),
                Items.hint("definir ici", "s'y teleporter")), e -> {
            if (e.isRightClick()) {
                var l = cur == null ? null : cur.toLocation();
                if (l != null) player.teleport(l);
                return;
            }
            setter.accept(Pos.of(player.getLocation()));
            save();
            refresh();
        });
    }

    private Menu rooms() {
        ListMenu<Room>[] self = new ListMenu[1];
        self[0] = new ListMenu<Room>(plugin, player, "<dark_gray>Salles de " + d.id, () -> d.rooms)
                .icon(r -> {
                    List<String> lore = new ArrayList<>();
                    lore.add("<gray>Vagues : <white>" + r.waves.size() + "<gray>  Boss : <white>" + (r.boss != null ? "oui" : "non"));
                    lore.add("<gray>Pieges : <white>" + r.traps.size() + "<gray>  Enigmes : <white>" + r.puzzles.size());
                    r.problems().forEach(s -> lore.add("<red>! " + Msg.escape(s)));
                    lore.add(Items.hint("editer", "supprimer (maj)"));
                    lore.add("<yellow>Maj + gauche</yellow> <gray>monter dans l'ordre");
                    return Items.of(Material.IRON_DOOR, "<gold>" + (d.rooms.indexOf(r) + 1) + ". " + Msg.escape(r.name), lore);
                })
                .onClick((r, e) -> {
                    int i = d.rooms.indexOf(r);
                    if (e.isShiftClick() && e.isRightClick()) {
                        d.rooms.remove(i);
                    } else if (e.isShiftClick() && i > 0) {
                        d.rooms.remove(i);
                        d.rooms.add(i - 1, r);
                    } else if (!e.isShiftClick() && e.isLeftClick()) {
                        new RoomMenu(plugin, player, d, r).open();
                        return;
                    } else {
                        return;
                    }
                    save();
                    self[0].refresh();
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter une salle"), e ->
                        plugin.prompts().ask(player, "Nom de la nouvelle salle :", v -> {
                            Room r = new Room();
                            r.name = v;
                            r.id = "room" + (d.rooms.size() + 1);
                            d.rooms.add(r);
                            save();
                            new RoomMenu(plugin, player, d, r).open();
                        }, self[0]::open))
                .back(() -> this);
        return self[0];
    }
}
