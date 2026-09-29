package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.integration.Hooks;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.MobSpec;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.model.SpawnPoint;
import io.github.youpl.onedonjon.util.Effects;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Edition complete d'un groupe de monstres (ou d'un boss). */
public final class MobMenu extends Menu {

    private final Dungeon d;
    private final Room r;
    private final MobSpec m;
    private final Supplier<Menu> back;

    public MobMenu(OneDonjonPlugin plugin, Player p, Dungeon d, Room r, MobSpec m, Supplier<Menu> back) {
        super(plugin, p);
        this.d = d;
        this.r = r;
        this.m = m;
        this.back = back;
    }

    private void save() {
        plugin.storage().save(d);
    }

    static Material egg(MobSpec m) {
        if (m.source == MobSpec.Source.MYTHICMOBS) return Material.DRAGON_EGG;
        Material mat = Material.matchMaterial(m.type + "_SPAWN_EGG");
        return mat == null ? Material.EGG : mat;
    }

    @Override protected String title() { return "<dark_gray>Monstre : " + Msg.escape(m.type); }
    @Override protected int rows() { return 4; }

    @Override
    protected void build() {
        cycle(10, Material.COMPARATOR, "<gold>Source", MobSpec.Source.values(), () -> m.source, s -> {
            if (s != m.source) m.type = s == MobSpec.Source.VANILLA ? "ZOMBIE" : "";
            m.source = s;
            save();
        }, plugin.hooks().mythic() ? "<green>MythicMobs detecte" : "<red>MythicMobs absent");

        set(11, Items.of(egg(m), "<gold>Type", "<gray>Actuel : <white>" + (m.type.isBlank() ? "<red>(aucun)" : Msg.escape(m.type)),
                Items.hint("choisir dans la liste", "saisir le nom")), e -> {
            if (e.isRightClick()) {
                plugin.prompts().ask(player, "Nom du type (" + (m.source == MobSpec.Source.VANILLA ? "EntityType" : "nom interne MythicMobs") + ") :",
                        v -> { m.type = v; save(); open(); }, this::open);
            } else {
                pickType();
            }
        });
        number(12, Material.ARROW, "<gold>Quantite", () -> (double) m.count, 1, 1, 200, v -> { m.count = v.intValue(); save(); });
        number(13, Material.GOLDEN_APPLE, "<gold>Points de vie", () -> m.health, 5, 0, 100_000,
                v -> { m.health = v; save(); }, "<dark_gray>0 = valeur par defaut du monstre");
        number(14, Material.IRON_SWORD, "<gold>Multiplicateur de degats", () -> m.damageMultiplier, 0.25, 0, 100,
                v -> { m.damageMultiplier = v; save(); }, "<dark_gray>1 = degats normaux");
        text(15, Material.NAME_TAG, "<gold>Nom affiche", () -> m.name, "Nom du monstre (MiniMessage, ex. <red>Gardien</red>) :",
                v -> { m.name = v; save(); });
        set(16, Items.of(Material.POTION, "<gold>Effets permanents", m.effects.isEmpty() ? "<gray>Aucun" : "<white>" + String.join(", ", m.effects),
                Items.hint("editer", null)), e -> effects().open());

        set(19, Items.of(Material.ARMOR_STAND, "<gold>Modele ModelEngine",
                "<gray>Actuel : <white>" + (m.model.isBlank() ? "<dark_gray>(aucun)" : Msg.escape(m.model)),
                plugin.hooks().modelEngine() ? "<green>ModelEngine detecte" : "<red>ModelEngine absent",
                Items.hint("choisir / saisir", "retirer")), e -> {
            if (e.isRightClick()) {
                m.model = "";
                save();
                refresh();
                return;
            }
            List<String> ids = plugin.hooks().modelIds();
            if (ids.isEmpty()) {
                plugin.prompts().ask(player, "Id du modele ModelEngine :", v -> { m.model = v; save(); open(); }, this::open);
            } else {
                ListMenu.pick(plugin, player, "<dark_gray>Modeles", ids, id -> Items.of(Material.ARMOR_STAND, "<white>" + Msg.escape(id)),
                        id -> { m.model = id; save(); open(); }, () -> this).open();
            }
        });

        List<String> sps = new ArrayList<>();
        sps.add("");
        for (SpawnPoint s : r.spawnPoints) sps.add(s.id());
        cycle(20, Material.ENDER_PEARL, "<gold>Point d'apparition", sps.toArray(new String[0]),
                () -> sps.contains(m.spawnPoint) ? m.spawnPoint : "", v -> { m.spawnPoint = v; save(); },
                "<dark_gray>(vide) = aleatoire parmi ceux de la salle");
        toggle(21, "<gold>Barre de vie (boss)", () -> m.bossBar, v -> { m.bossBar = v; save(); });
        set(22, Items.of(Material.WRITABLE_BOOK, "<gold>Actions a la mort", "<gray>" + m.onDeath.size() + " action(s)",
                Items.hint("editer", null)), e -> ActionsMenu.of(plugin, player, "<dark_gray>Mort du monstre", m.onDeath, this::save, () -> this).open());
        fillBottom();
        backButton(back);
    }

    private void pickType() {
        if (m.source == MobSpec.Source.VANILLA) {
            ListMenu.pick(plugin, player, "<dark_gray>Monstres vanilla", Hooks.spawnableMobTypes(),
                    t -> {
                        Material mat = Material.matchMaterial(t.name() + "_SPAWN_EGG");
                        return Items.of(mat == null ? Material.EGG : mat, "<white>" + t.name());
                    }, (EntityType t) -> { m.type = t.name(); save(); open(); }, () -> this).open();
            return;
        }
        List<String> names = plugin.hooks().mythicMobNames();
        if (names.isEmpty()) {
            plugin.prompts().ask(player, "Nom interne du MythicMob :", v -> { m.type = v; save(); open(); }, this::open);
            return;
        }
        ListMenu.pick(plugin, player, "<dark_gray>MythicMobs", names,
                n -> Items.of(Material.DRAGON_EGG, "<white>" + Msg.escape(n)),
                n -> { m.type = n; save(); open(); }, () -> this).open();
    }

    private Menu effects() {
        ListMenu<String>[] self = new ListMenu[1];
        self[0] = new ListMenu<String>(plugin, player, "<dark_gray>Effets", () -> m.effects)
                .icon(s -> Items.of(Material.POTION, "<white>" + s, Items.hint(null, "supprimer")))
                .onClick((s, e) -> {
                    if (e.isRightClick()) {
                        m.effects.remove(s);
                        save();
                        self[0].refresh();
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter un effet"), e ->
                        ListMenu.pick(plugin, player, "<dark_gray>Effets", Effects.names(),
                                n -> Items.of(Material.POTION, "<white>" + n),
                                n -> plugin.prompts().ask(player, "Niveau de " + n + " (1 = normal) :", v -> {
                                    m.effects.add(n + ":" + Integer.parseInt(v.trim()));
                                    save();
                                    self[0].open();
                                }, self[0]::open), () -> self[0]).open())
                .back(() -> this);
        return self[0];
    }
}
