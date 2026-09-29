package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;
import java.util.List;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Cuboid;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.MobSpec;
import io.github.youpl.onedonjon.model.Pos;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.model.SpawnPoint;
import io.github.youpl.onedonjon.model.Wave;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class RoomMenu extends Menu {

    private final Dungeon d;
    private final Room r;

    public RoomMenu(OneDonjonPlugin plugin, Player p, Dungeon d, Room r) {
        super(plugin, p);
        this.d = d;
        this.r = r;
    }

    private void save() {
        plugin.storage().save(d);
    }

    @Override protected String title() { return "<dark_gray>Salle : " + Msg.escape(r.name); }
    @Override protected int rows() { return 5; }

    @Override
    protected void build() {
        text(10, Material.NAME_TAG, "<gold>Nom", () -> r.name, "Nouveau nom de la salle :", v -> { r.name = v; save(); });
        region(11, Material.GRASS_BLOCK, "<green>Zone de la salle", r.region,
                "<gray>Un joueur qui y entre l'active.", c -> r.region = c, false);
        region(12, Material.IRON_BARS, "<red>Zone de la porte", r.door,
                "<gray>Verrouillee jusqu'a la fin de la salle.", c -> r.door = c, true);
        text(13, Material.IRON_BLOCK, "<gold>Materiau de la porte", () -> r.doorMaterial, "Materiau de la porte (ex. IRON_BARS, OBSIDIAN) :", v -> {
            Material m = Material.matchMaterial(v);
            if (m == null || !m.isBlock()) throw new IllegalArgumentException("materiau inconnu");
            r.doorMaterial = m.name();
            save();
        });
        set(14, Items.of(Material.RESPAWN_ANCHOR, "<gold>Point de reapparition",
                "<gray>Actuel : <white>" + (r.checkpoint == null ? "<dark_gray>(depart du donjon)" : r.checkpoint.describe()),
                Items.hint("definir ici", "effacer")), e -> {
            r.checkpoint = e.isRightClick() ? null : Pos.of(player.getLocation());
            save();
            refresh();
        });

        set(19, Items.of(Material.ARMOR_STAND, "<gold>Points d'apparition", "<gray>" + r.spawnPoints.size() + " point(s)",
                Items.hint("editer", null)), e -> spawnPoints().open());
        set(20, Items.of(Material.ZOMBIE_HEAD, "<gold>Vagues de monstres", "<gray>" + r.waves.size() + " vague(s)",
                Items.hint("editer", null)), e -> waves().open());
        set(21, Items.of(Material.WITHER_SKELETON_SKULL, "<dark_red>Boss",
                r.boss == null ? "<gray>Aucun" : "<white>" + r.boss.describe(),
                Items.hint(r.boss == null ? "creer" : "editer", "supprimer")), e -> {
            if (e.isRightClick()) {
                r.boss = null;
                save();
                refresh();
                return;
            }
            if (r.boss == null) {
                r.boss = new MobSpec();
                r.boss.bossBar = true;
                r.boss.health = 200;
                save();
            }
            new MobMenu(plugin, player, d, r, r.boss, () -> this).open();
        });
        set(22, Items.of(Material.TRIPWIRE_HOOK, "<gold>Pieges", "<gray>" + r.traps.size() + " piege(s)", Items.hint("editer", null)),
                e -> TrapMenu.list(plugin, player, d, r, () -> this).open());
        set(23, Items.of(Material.LEVER, "<gold>Enigmes", "<gray>" + r.puzzles.size() + " enigme(s)", Items.hint("editer", null)),
                e -> PuzzleMenu.list(plugin, player, d, r, () -> this).open());
        set(24, Items.of(Material.VILLAGER_SPAWN_EGG, "<gold>PNJ (Citizens)", "<gray>" + r.npcs.size() + " PNJ",
                plugin.hooks().citizens() ? "<green>Citizens detecte" : "<red>Citizens absent (PNJ ignores)",
                Items.hint("editer", null)), e -> NpcMenu.list(plugin, player, d, r, () -> this).open());
        set(28, Items.of(Material.WRITABLE_BOOK, "<gold>Actions a l'entree", "<gray>" + r.onEnter.size() + " action(s)",
                Items.hint("editer", null)), e -> ActionsMenu.of(plugin, player, "<dark_gray>Entree", r.onEnter, this::save, () -> this).open());
        set(29, Items.of(Material.WRITABLE_BOOK, "<gold>Actions a la fin", "<gray>" + r.onClear.size() + " action(s)",
                Items.hint("editer", null)), e -> ActionsMenu.of(plugin, player, "<dark_gray>Fin de salle", r.onClear, this::save, () -> this).open());

        List<String> pb = r.problems();
        List<String> lore = new ArrayList<>();
        if (pb.isEmpty()) lore.add("<green>Aucun probleme.");
        else pb.forEach(s -> lore.add("<red>- " + Msg.escape(s)));
        set(31, Items.of(pb.isEmpty() ? Material.EMERALD : Material.REDSTONE, "<gold>Verification", lore), null);
        fillBottom();
        backButton(() -> new DungeonMenu(plugin, player, d));
    }

    private void region(int slot, Material mat, String label, Cuboid cur, String desc,
                        java.util.function.Consumer<Cuboid> setter, boolean limitVolume) {
        set(slot, Items.of(mat, label, desc, "<gray>Actuel : <white>" + (cur == null ? "<red>non defini" : cur.describe()),
                Items.hint("prendre la selection (baguette)", "effacer")), e -> {
            if (e.isRightClick()) {
                setter.accept(null);
            } else {
                Cuboid c = plugin.selections().get(player);
                if (c == null) {
                    Msg.error(player, "Aucune selection : utilisez la baguette (/donjon wand), clic gauche et droit.");
                    return;
                }
                if (limitVolume && c.volume() > 20_000) {
                    Msg.error(player, "Porte trop grande (" + c.volume() + " blocs, maximum 20000).");
                    return;
                }
                setter.accept(c);
            }
            save();
            refresh();
        });
    }

    private Menu spawnPoints() {
        ListMenu<SpawnPoint>[] self = new ListMenu[1];
        self[0] = new ListMenu<SpawnPoint>(plugin, player, "<dark_gray>Points d'apparition", () -> r.spawnPoints)
                .icon(s -> Items.of(Material.ARMOR_STAND, "<gold>" + s.id(), "<gray>" + s.pos().describe(),
                        Items.hint("se teleporter", "supprimer")))
                .onClick((s, e) -> {
                    if (e.isRightClick()) {
                        r.spawnPoints.remove(s);
                        save();
                        self[0].refresh();
                    } else if (s.pos().toLocation() != null) {
                        player.teleport(s.pos().toLocation());
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter ici", "<gray>A votre position actuelle."), e -> {
                    r.spawnPoints.add(new SpawnPoint(nextId(), Pos.of(player.getLocation())));
                    save();
                    self[0].refresh();
                })
                .back(() -> this);
        return self[0];
    }

    private String nextId() {
        int n = r.spawnPoints.size() + 1;
        while (true) {
            String id = "sp" + n;
            boolean used = false;
            for (SpawnPoint s : r.spawnPoints) if (s.id().equals(id)) used = true;
            if (!used) return id;
            n++;
        }
    }

    private Menu waves() {
        ListMenu<Wave>[] self = new ListMenu[1];
        self[0] = new ListMenu<Wave>(plugin, player, "<dark_gray>Vagues", () -> r.waves)
                .icon(w -> {
                    List<String> lore = new ArrayList<>();
                    lore.add("<gray>Delai : <white>" + w.delayTicks / 20.0 + " s");
                    for (MobSpec m : w.mobs) lore.add("<gray> - " + m.describe());
                    lore.add(Items.hint("editer", "supprimer (maj)"));
                    return Items.of(Material.ZOMBIE_HEAD, "<gold>Vague " + (r.waves.indexOf(w) + 1)
                            + " <dark_gray>(" + w.totalMobs() + " monstres)", lore);
                })
                .onClick((w, e) -> {
                    if (e.isRightClick() && e.isShiftClick()) {
                        r.waves.remove(w);
                        save();
                        self[0].refresh();
                    } else if (e.isLeftClick()) {
                        new WaveMenu(plugin, player, d, r, w, () -> self[0]).open();
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter une vague"), e -> {
                    Wave w = new Wave();
                    r.waves.add(w);
                    save();
                    new WaveMenu(plugin, player, d, r, w, () -> self[0]).open();
                })
                .back(() -> this);
        return self[0];
    }
}
