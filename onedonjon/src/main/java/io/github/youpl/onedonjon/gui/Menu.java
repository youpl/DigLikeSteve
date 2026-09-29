package io.github.youpl.onedonjon.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Base des menus : cases -> actions, reconstruction complete a chaque {@link #refresh()}. */
public abstract class Menu implements InventoryHolder {

    protected final OneDonjonPlugin plugin;
    protected final Player player;
    private Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected Menu(OneDonjonPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected abstract String title();

    protected abstract int rows();

    protected abstract void build();

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        inv = Bukkit.createInventory(this, rows() * 9, Msg.mm(title()));
        build();
        player.openInventory(inv);
    }

    public void refresh() {
        if (inv == null) {
            open();
            return;
        }
        inv.clear();
        handlers.clear();
        build();
    }

    void handle(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> h = handlers.get(e.getRawSlot());
        if (h != null) h.accept(e);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inv.setItem(slot, item);
        if (onClick != null) handlers.put(slot, onClick);
    }

    protected void fillBottom() {
        ItemStack pane = Items.of(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = (rows() - 1) * 9; s < rows() * 9; s++) inv.setItem(s, pane);
    }

    /** Bouton retour en bas a gauche. */
    protected void backButton(Supplier<Menu> parent) {
        set(rows() * 9 - 9, Items.of(Material.ARROW, "<yellow>Retour"), e -> parent.get().open());
    }

    // ------------------------------------------------------------ boutons courants

    /** Nombre reglable : gauche +pas, droit -pas, maj x10. */
    protected void number(int slot, Material mat, String label, Supplier<Double> get, double step, double min,
                          double max, Consumer<Double> set, String... extraLore) {
        String[] lore = new String[2 + extraLore.length];
        lore[0] = "<gray>Valeur : <white>" + fmt(get.get());
        lore[1] = Items.hint("+" + fmt(step), "-" + fmt(step)) + " <dark_gray>(maj = x10)";
        System.arraycopy(extraLore, 0, lore, 2, extraLore.length);
        set(slot, Items.of(mat, label, lore), e -> {
            double d = step * (e.isShiftClick() ? 10 : 1) * (e.isRightClick() ? -1 : 1);
            set.accept(Math.max(min, Math.min(max, Math.round((get.get() + d) * 1000) / 1000.0)));
            refresh();
        });
    }

    /** Valeur parmi une liste : gauche suivant, droit precedent. */
    protected <T> void cycle(int slot, Material mat, String label, T[] values, Supplier<T> get, Consumer<T> set,
                             String... extraLore) {
        String[] lore = new String[2 + extraLore.length];
        lore[0] = "<gray>Actuel : <white>" + get.get();
        lore[1] = Items.hint("suivant", "precedent");
        System.arraycopy(extraLore, 0, lore, 2, extraLore.length);
        set(slot, Items.of(mat, label, lore), e -> {
            int i = 0;
            for (; i < values.length; i++) if (values[i].equals(get.get())) break;
            int n = (i + (e.isRightClick() ? values.length - 1 : 1)) % values.length;
            set.accept(values[n]);
            refresh();
        });
    }

    /** Texte libre saisi dans le chat. */
    protected void text(int slot, Material mat, String label, Supplier<String> get, String question,
                        Consumer<String> set, String... extraLore) {
        String[] lore = new String[2 + extraLore.length];
        String cur = get.get();
        lore[0] = "<gray>Actuel : <white>" + (cur == null || cur.isBlank() ? "<dark_gray>(vide)" : Msg.escape(cur));
        lore[1] = Items.hint("modifier", null);
        System.arraycopy(extraLore, 0, lore, 2, extraLore.length);
        set(slot, Items.of(mat, label, lore), e -> plugin.prompts().ask(player, question, v -> {
            set.accept(v);
            open();
        }, this::open));
    }

    protected void toggle(int slot, String label, Supplier<Boolean> get, Consumer<Boolean> set, String... extraLore) {
        boolean on = get.get();
        String[] lore = new String[2 + extraLore.length];
        lore[0] = on ? "<green>Active" : "<red>Desactive";
        lore[1] = Items.hint("basculer", null);
        System.arraycopy(extraLore, 0, lore, 2, extraLore.length);
        set(slot, Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE, label, lore), e -> {
            set.accept(!get.get());
            refresh();
        });
    }

    protected static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
