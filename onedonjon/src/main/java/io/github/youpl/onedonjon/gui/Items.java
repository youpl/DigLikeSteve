package io.github.youpl.onedonjon.gui;

import java.util.ArrayList;
import java.util.List;

import io.github.youpl.onedonjon.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class Items {

    private Items() {}

    /** Objet de menu : nom et lignes de lore en MiniMessage. */
    public static ItemStack of(Material mat, String name, String... lore) {
        return of(mat, name, List.of(lore));
    }

    public static ItemStack of(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.plain(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.plain(s));
        m.lore(l);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    public static String hint(String left, String right) {
        StringBuilder sb = new StringBuilder();
        if (left != null) sb.append("<yellow>Clic gauche</yellow> <gray>").append(left);
        if (right != null) sb.append(sb.isEmpty() ? "" : "  ").append("<yellow>Clic droit</yellow> <gray>").append(right);
        return sb.toString();
    }
}
