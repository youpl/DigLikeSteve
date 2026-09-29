package io.github.youpl.onedonjon.gui;

import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Dungeon;
import io.github.youpl.onedonjon.model.Pos;
import io.github.youpl.onedonjon.model.Room;
import io.github.youpl.onedonjon.model.Trap;
import io.github.youpl.onedonjon.util.Effects;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class TrapMenu extends Menu {

    private final Dungeon d;
    private final Trap t;
    private final Supplier<Menu> back;

    private TrapMenu(OneDonjonPlugin plugin, Player p, Dungeon d, Trap t, Supplier<Menu> back) {
        super(plugin, p);
        this.d = d;
        this.t = t;
        this.back = back;
    }

    public static Menu list(OneDonjonPlugin plugin, Player p, Dungeon d, Room r, Supplier<Menu> back) {
        ListMenu<Trap>[] self = new ListMenu[1];
        self[0] = new ListMenu<Trap>(plugin, p, "<dark_gray>Pieges", () -> r.traps)
                .icon(t -> Items.of(icon(t.type), "<gold>" + t.id + " <dark_gray>(" + t.type + ")",
                        "<gray>Position : <white>" + (t.pos == null ? "<red>non definie" : t.pos.describe()),
                        Items.hint("editer", "supprimer (maj)")))
                .onClick((t, e) -> {
                    if (e.isRightClick() && e.isShiftClick()) {
                        r.traps.remove(t);
                        plugin.storage().save(d);
                        self[0].refresh();
                    } else if (e.isLeftClick()) {
                        new TrapMenu(plugin, p, d, t, () -> self[0]).open();
                    }
                })
                .button(() -> Items.of(Material.LIME_DYE, "<green>Ajouter un piege ici", "<gray>Cree a votre position."), e -> {
                    Trap t = new Trap();
                    t.id = "trap" + (r.traps.size() + 1);
                    t.pos = Pos.of(p.getLocation());
                    r.traps.add(t);
                    plugin.storage().save(d);
                    new TrapMenu(plugin, p, d, t, () -> self[0]).open();
                })
                .back(back);
        return self[0];
    }

    static Material icon(Trap.Type t) {
        return switch (t) {
            case DAMAGE_ZONE -> Material.IRON_SWORD;
            case POTION_ZONE -> Material.POTION;
            case FIRE_ZONE -> Material.BLAZE_POWDER;
            case ARROW_TURRET -> Material.DISPENSER;
            case LIGHTNING -> Material.LIGHTNING_ROD;
            case LAUNCHER -> Material.SLIME_BLOCK;
            case TNT -> Material.TNT;
        };
    }

    private void save() {
        plugin.storage().save(d);
    }

    @Override protected String title() { return "<dark_gray>Piege : " + Msg.escape(t.id); }
    @Override protected int rows() { return 4; }

    @Override
    protected void build() {
        cycle(10, icon(t.type), "<gold>Type", Trap.Type.values(), () -> t.type, v -> { t.type = v; save(); },
                "<dark_gray>" + describe(t.type));
        cycle(11, Material.REPEATER, "<gold>Declenchement", Trap.Mode.values(), () -> t.mode, v -> { t.mode = v; save(); },
                "<dark_gray>CONTINUOUS : a chaque intervalle", "<dark_gray>ON_STEP : quand un joueur entre");
        set(12, Items.of(Material.COMPASS, "<gold>Position", "<gray>" + (t.pos == null ? "<red>non definie" : t.pos.describe()),
                Items.hint("definir ici", "s'y teleporter")), e -> {
            if (e.isRightClick()) {
                if (t.pos != null && t.pos.toLocation() != null) player.teleport(t.pos.toLocation());
                return;
            }
            t.pos = Pos.of(player.getLocation());
            save();
            refresh();
        });
        number(13, Material.HEART_OF_THE_SEA, "<gold>Rayon", () -> t.radius, 0.5, 0.5, 30, v -> { t.radius = v; save(); });
        number(14, Material.REDSTONE, "<gold>Valeur", () -> t.value, 0.5, 0, 100, v -> { t.value = v; save(); },
                "<dark_gray>Degats / hauteur du lanceur / puissance de l'explosion");
        number(15, Material.CLOCK, "<gold>Intervalle (ticks)", () -> (double) t.intervalTicks, 5, 5, 1200,
                v -> { t.intervalTicks = v.intValue(); save(); }, "<dark_gray>20 ticks = 1 seconde");
        if (t.type == Trap.Type.POTION_ZONE) {
            text(16, Material.BREWING_STAND, "<gold>Effet", () -> t.effect, "Effet au format NOM:niveau (ex. SLOWNESS:2, POISON:1) :", v -> {
                if (Effects.parse(v, 20) == null) throw new IllegalArgumentException("effet inconnu");
                t.effect = v.toUpperCase();
                save();
            });
        }
        fillBottom();
        backButton(back);
    }

    private static String describe(Trap.Type t) {
        return switch (t) {
            case DAMAGE_ZONE -> "Degats dans la zone";
            case POTION_ZONE -> "Effet de potion dans la zone";
            case FIRE_ZONE -> "Enflamme les joueurs";
            case ARROW_TURRET -> "Tire des fleches sur le plus proche";
            case LIGHTNING -> "Foudre + degats";
            case LAUNCHER -> "Projette en l'air";
            case TNT -> "Explosion sans degats aux blocs (1,5 s)";
        };
    }
}
