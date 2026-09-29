package io.github.youpl.onedonjon.gui;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final OneDonjonPlugin plugin;

    public MenuListener(OneDonjonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (menu.adminOnly()) {
            if (!plugin.core().isAdmin(p)) {
                p.closeInventory();
                Msg.error(p, "Permission manquante.");
                return;
            }
            String editing = plugin.editing(p);
            if (editing != null && plugin.runs().of(editing) != null) {
                p.closeInventory();
                Msg.error(p, "Ce donjon est en cours de partie : edition impossible tant qu'elle n'est pas terminee.");
                return;
            }
        }
        plugin.setActor(p);
        try {
            menu.handle(e);
        } finally {
            plugin.setActor(null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
