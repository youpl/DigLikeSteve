package io.github.youpl.onedonjon.gui;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    public MenuListener(OneDonjonPlugin plugin) {
        // pas d'etat : le menu est retrouve via le holder de l'inventaire
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory())) {
            menu.handle(e);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
