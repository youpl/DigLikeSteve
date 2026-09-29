package io.github.youpl.onedonjon.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.youpl.onedonjon.OneDonjonPlugin;
import io.github.youpl.onedonjon.model.Cuboid;
import io.github.youpl.onedonjon.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Baguette de selection : clic gauche = coin 1, clic droit = coin 2. */
public final class Selections implements Listener {

    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> sel = new HashMap<>();

    private final OneDonjonPlugin plugin;

    public Selections(OneDonjonPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "wand");
    }

    public ItemStack wand() {
        ItemStack it = Items.of(Material.BLAZE_ROD, "<gold>Baguette OneDonjon",
                "<yellow>Clic gauche</yellow> <gray>coin 1", "<yellow>Clic droit</yellow> <gray>coin 2");
        var m = it.getItemMeta();
        m.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** Selection complete, ou null. */
    public Cuboid get(Player p) {
        Location[] s = sel.get(p.getUniqueId());
        if (s == null || s[0] == null || s[1] == null || !s[0].getWorld().equals(s[1].getWorld())) return null;
        return Cuboid.of(s[0], s[1]);
    }

    private boolean isWand(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null || !isWand(e.getItem())) return;
        if (!plugin.core().isAdmin(e.getPlayer())) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Location[] s = sel.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new Location[2]);
        int i = a == Action.LEFT_CLICK_BLOCK ? 0 : 1;
        s[i] = e.getClickedBlock().getLocation();
        Location l = s[i];
        Msg.send(e.getPlayer(), "Coin " + (i + 1) + " : <white>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sel.remove(e.getPlayer().getUniqueId());
    }
}
