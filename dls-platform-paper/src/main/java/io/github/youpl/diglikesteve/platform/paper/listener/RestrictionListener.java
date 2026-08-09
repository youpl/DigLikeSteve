package io.github.youpl.diglikesteve.platform.paper.listener;

import io.github.youpl.diglikesteve.application.service.RestrictionPolicy;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.game.GameSession;
import io.github.youpl.diglikesteve.core.game.SessionPhase;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Application des politiques de restriction (DAT §9). Chaque gestionnaire est court : il
 * rassemble le contexte et applique la décision prise par {@link RestrictionPolicy}.
 */
public final class RestrictionListener implements Listener {

    private final DlsServices services;

    public RestrictionListener(DlsServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    private SessionPhase phase() {
        return services.lifecycle().phase();
    }

    private boolean isParticipant(Player player) {
        return services.lifecycle().session()
                .flatMap(session -> session.participant(PlayerId.of(player.getUniqueId())))
                .isPresent();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (RestrictionPolicy.blocksPlacement(phase(), isParticipant(event.getPlayer()))) {
            event.setCancelled(true);
            services.notifications().message(PlayerId.of(event.getPlayer().getUniqueId()), "restriction.place");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (RestrictionPolicy.blocksItemTransfer(phase(), isParticipant(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && RestrictionPolicy.blocksItemTransfer(phase(), isParticipant(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.PLAYER) {
            return;
        }
        if (RestrictionPolicy.blocksContainerAccess(phase(), isParticipant(player))) {
            event.setCancelled(true);
            services.notifications().message(PlayerId.of(player.getUniqueId()), "restriction.container");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && RestrictionPolicy.blocksContainerAccess(phase(), isParticipant(player))) {
            event.setCancelled(true);
        }
    }

    /** Le kit ne peut pas être déplacé hors de ses slots ni stocké ailleurs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isParticipant(player)) {
            return;
        }
        if (isKitItem(event.getCurrentItem()) || isKitItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isParticipant(player)
                && isKitItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    private boolean isKitItem(ItemStack stack) {
        return services.kits().isKitItem(stack);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (RestrictionPolicy.blocksWorldChange(phase(), isParticipant(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        boolean pluginCause = event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN;
        if (RestrictionPolicy.blocksTeleport(phase(), isParticipant(event.getPlayer()), pluginCause)) {
            event.setCancelled(true);
            services.notifications().message(PlayerId.of(event.getPlayer().getUniqueId()), "restriction.teleport");
        }
    }

    /** Un liquide ne doit pas franchir la frontière de l'arène. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        ArenaBounds bounds = bounds();
        if (bounds == null) {
            return;
        }
        boolean fromInside = contains(bounds, event.getBlock());
        boolean toInside = contains(bounds, event.getToBlock());
        if (RestrictionPolicy.blocksBoundaryCrossing(fromInside, toInside)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        cancelIfCrossesBoundary(event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        cancelIfCrossesBoundary(event.getBlocks(), event);
    }

    private void cancelIfCrossesBoundary(List<Block> blocks, org.bukkit.event.Cancellable event) {
        ArenaBounds bounds = bounds();
        if (bounds == null || blocks.isEmpty()) {
            return;
        }
        for (Block block : blocks) {
            if (contains(bounds, block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private ArenaBounds bounds() {
        return services.lifecycle().session()
                .flatMap(GameSession::arena)
                .map(arena -> arena.bounds())
                .orElse(null);
    }

    private static boolean contains(ArenaBounds bounds, Block block) {
        return bounds.worldName().equals(block.getWorld().getName())
                && bounds.contains(block.getX(), block.getY(), block.getZ());
    }
}
