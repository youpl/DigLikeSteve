package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Menu pagine generique : 45 cases de contenu + barre de boutons en bas. */
public final class ListMenu<T> extends Menu {

    private static final int PAGE = 45;
    private static final int[] EXTRA_SLOTS = {46, 47, 49, 51, 52, 53};

    private record Btn(Supplier<ItemStack> item, Consumer<InventoryClickEvent> click) {}

    private final String title;
    private final Supplier<List<T>> items;
    private Function<T, ItemStack> icon = t -> Items.of(Material.PAPER, String.valueOf(t));
    private BiConsumer<T, InventoryClickEvent> onClick = (t, e) -> { };
    private Supplier<Menu> back;
    private final List<Btn> buttons = new ArrayList<>();
    private boolean adminOnly = true;
    private int page;

    public ListMenu(OneDonjonPlugin plugin, Player player, String title, Supplier<List<T>> items) {
        super(plugin, player);
        this.title = title;
        this.items = items;
    }

    public ListMenu<T> icon(Function<T, ItemStack> f) { this.icon = f; return this; }
    public ListMenu<T> onClick(BiConsumer<T, InventoryClickEvent> f) { this.onClick = f; return this; }
    /** Menu accessible a tous les joueurs (ex. liste des donjons jouables). */
    public ListMenu<T> publicMenu() { this.adminOnly = false; return this; }

    @Override public boolean adminOnly() { return adminOnly; }

    public ListMenu<T> back(Supplier<Menu> b) { this.back = b; return this; }

    /** Ajoute un bouton dans la barre du bas (jusqu'a 6). */
    public ListMenu<T> button(Supplier<ItemStack> item, Consumer<InventoryClickEvent> click) {
        buttons.add(new Btn(item, click));
        return this;
    }

    @Override protected String title() { return title; }
    @Override protected int rows() { return 6; }

    @Override
    protected void build() {
        List<T> all = items.get();
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < PAGE; i++) {
            int idx = page * PAGE + i;
            if (idx >= all.size()) break;
            T t = all.get(idx);
            set(i, icon.apply(t), e -> {
                onClick.accept(t, e);
            });
        }
        fillBottom();
        if (back != null) backButton(back);
        for (int i = 0; i < buttons.size() && i < EXTRA_SLOTS.length; i++) {
            Btn b = buttons.get(i);
            set(EXTRA_SLOTS[i], b.item().get(), b.click());
        }
        if (page > 0) set(48, Items.of(Material.SPECTRAL_ARROW, "<yellow>Page precedente"), e -> { page--; refresh(); });
        if (page < pages - 1) set(50, Items.of(Material.SPECTRAL_ARROW, "<yellow>Page suivante"), e -> { page++; refresh(); });
    }

    /** Selecteur simple : choisir une valeur parmi une liste. */
    public static <V> ListMenu<V> pick(OneDonjonPlugin plugin, Player player, String title, List<V> values,
                                       Function<V, ItemStack> icon, Consumer<V> onPick, Supplier<Menu> back) {
        return new ListMenu<V>(plugin, player, title, () -> values)
                .icon(icon)
                .onClick((v, e) -> onPick.accept(v))
                .back(back);
    }
}
