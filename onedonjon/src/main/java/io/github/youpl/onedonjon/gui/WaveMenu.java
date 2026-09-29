package io.github.youpl.onedonjon.gui;

import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.MobSpec;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.model.Wave;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class WaveMenu extends Menu {

    private final Dungeon d;
    private final Room r;
    private final Wave w;
    private final Supplier<Menu> back;

    public WaveMenu(OneDonjonPlugin plugin, Player p, Dungeon d, Room r, Wave w, Supplier<Menu> back) {
        super(plugin, p);
        this.d = d;
        this.r = r;
        this.w = w;
        this.back = back;
    }

    private void save() {
        plugin.storage().save(d);
    }

    @Override protected String title() { return "<dark_gray>Vague " + (r.waves.indexOf(w) + 1); }
    @Override protected int rows() { return 3; }

    @Override
    protected void build() {
        number(10, Material.CLOCK, "<gold>Delai avant apparition (ticks)", () -> (double) w.delayTicks, 20, 0, 12_000,
                v -> { w.delayTicks = v.intValue(); save(); }, "<dark_gray>20 ticks = 1 seconde");
        set(12, Items.of(Material.ZOMBIE_SPAWN_EGG, "<gold>Monstres", "<gray>" + w.mobs.size() + " groupe(s), "
                + w.totalMobs() + " monstres", Items.hint("editer", null)), e -> mobs().open());
        set(14, Items.of(Material.WRITABLE_BOOK, "<gold>Actions au debut de la vague", "<gray>" + w.onStart.size() + " action(s)",
                Items.hint("editer", null)), e -> ActionsMenu.of(plugin, player, "<dark_gray>Debut de vague", w.onStart, this::save, () -> this).open());
        fillBottom();
        backButton(back);
    }

    private Menu mobs() {
        ListMenu<MobSpec>[] self = new ListMenu[1];
        self[0] = new ListMenu<MobSpec>(plugin, player, "<dark_gray>Monstres de la vague", () -> w.mobs)
                .icon(m -> Items.of(MobMenu.egg(m), "<gold>" + m.describe(),
                        "<gray>Vie : <white>" + (m.health <= 0 ? "defaut" : fmt(m.health)),
                        "<gray>Degats : <white>x" + fmt(m.damageMultiplier),
                        Items.hint("editer", "supprimer (maj)")))
                .onClick((m, e) -> {
                    if (e.isRightClick() && e.isShiftClick()) {
                        w.mobs.remove(m);
                        save();
                        self[0].refresh();
                    } else if (e.isLeftClick()) {
                        new MobMenu(plugin, player, d, r, m, () -> self[0]).open();
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter un groupe de monstres"), e -> {
                    MobSpec m = new MobSpec();
                    w.mobs.add(m);
                    save();
                    new MobMenu(plugin, player, d, r, m, () -> self[0]).open();
                })
                .back(() -> this);
        return self[0];
    }
}
