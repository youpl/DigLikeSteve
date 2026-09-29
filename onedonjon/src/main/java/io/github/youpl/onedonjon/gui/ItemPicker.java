package io.github.youpl.onedonjon.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.ItemRef;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Choix d'un objet : vanilla (main ou nom), MMOItems ou Nexo, puis quantite. */
public final class ItemPicker extends Menu {

    private final Consumer<ItemRef> done;
    private final Supplier<Menu> back;

    private ItemPicker(OneDonjonPlugin plugin, Player p, Consumer<ItemRef> done, Supplier<Menu> back) {
        super(plugin, p);
        this.done = done;
        this.back = back;
    }

    public static void pick(OneDonjonPlugin plugin, Player p, Consumer<ItemRef> done, Supplier<Menu> back) {
        new ItemPicker(plugin, p, done, back).open();
    }

    @Override protected String title() { return "<dark_gray>Choisir un objet"; }
    @Override protected int rows() { return 3; }

    @Override
    protected void build() {
        var h = plugin.hooks();
        set(10, Items.of(Material.GRASS_BLOCK, "<green>Objet vanilla",
                Items.hint("prendre l'objet en main", "saisir un nom de materiau")), e -> {
            if (e.isRightClick()) {
                plugin.prompts().ask(player, "Nom du materiau (ex. DIAMOND_SWORD) :", v -> {
                    Material m = Material.matchMaterial(v);
                    if (m == null || m.isAir()) throw new IllegalArgumentException("materiau inconnu");
                    amount(ItemRef.Source.VANILLA, m.name());
                }, this::open);
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                Msg.error(player, "Tenez un objet en main, ou faites un clic droit pour saisir un nom.");
                return;
            }
            amount(ItemRef.Source.VANILLA, hand.getType().name());
        });
        source(12, Material.DIAMOND_SWORD, "<aqua>MMOItems", h.mmoItems(), ItemRef.Source.MMOITEMS, h.mmoItemIds(),
                "Saisir 'TYPE:ID' (ex. SWORD:CUTLASS) :");
        source(14, Material.AMETHYST_SHARD, "<light_purple>Nexo", h.nexo(), ItemRef.Source.NEXO, h.nexoItemIds(),
                "Saisir l'id Nexo :");
        backButton(back);
    }

    private void source(int slot, Material icon, String label, boolean available, ItemRef.Source src,
                        List<String> ids, String question) {
        if (!available) {
            set(slot, Items.of(Material.BARRIER, label, "<red>Plugin non detecte"), null);
            return;
        }
        set(slot, Items.of(icon, label, "<gray>" + ids.size() + " objet(s)", Items.hint("choisir dans la liste", "saisir l'id")), e -> {
            if (e.isRightClick() || ids.isEmpty()) {
                plugin.prompts().ask(player, question, v -> amount(src, v), this::open);
            } else {
                ListMenu.pick(plugin, player, label, ids, id -> Items.of(icon, "<white>" + Msg.escape(id)),
                        id -> amount(src, id), () -> this).open();
            }
        });
    }

    private void amount(ItemRef.Source src, String id) {
        plugin.prompts().ask(player, "Quantite (nombre entier) :", v -> {
            int n = Integer.parseInt(v.trim());
            if (n < 1 || n > 2304) throw new IllegalArgumentException("quantite hors limites");
            done.accept(new ItemRef(src, id, n));
        }, this::open);
    }
}
