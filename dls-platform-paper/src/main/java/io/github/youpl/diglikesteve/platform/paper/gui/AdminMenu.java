package io.github.youpl.diglikesteve.platform.paper.gui;

import io.github.youpl.diglikesteve.application.command.Commands;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.bootstrap.DlsServices;
import io.github.youpl.diglikesteve.platform.paper.command.SelectionDraft;
import io.github.youpl.diglikesteve.platform.paper.config.ConfigurationLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Menu d'administration en jeu (DAT §6.1 et §22, lot 5).
 *
 * <p>Le menu ne modifie qu'un brouillon : la validation et l'écriture de la configuration sont
 * atomiques et refusées hors de la phase {@code IDLE}.</p>
 */
public final class AdminMenu implements Listener {

    private static final int SIZE = 27;

    private final DlsServices services;
    private final ConfigurationLoader configuration;
    private final SelectionDraft selection;

    public AdminMenu(DlsServices services, ConfigurationLoader configuration, SelectionDraft selection) {
        this.services = Objects.requireNonNull(services, "services");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    public void open(Player player) {
        FileConfiguration layout = configuration.read("gui.yml");
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                services.messages().renderRaw(layout.getString("title", "<dark_gray>Dig Like Steve</dark_gray>")));
        holder.inventory = inventory;

        inventory.setItem(10, button(Material.GOLDEN_PICKAXE, "prepare",
                "<green>Préparer et lancer</green>",
                "<gray>Écrase la zone puis ouvre les inscriptions.</gray>"));
        inventory.setItem(11, button(Material.BARRIER, "stop",
                "<red>Arrêter la partie</red>",
                "<gray>Idempotent : ni double restauration, ni double récompense.</gray>"));
        inventory.setItem(12, button(Material.WOODEN_AXE, "pos1",
                "<yellow>Coin 1</yellow>", "<gray>Utilise votre position actuelle.</gray>"));
        inventory.setItem(13, button(Material.IRON_AXE, "pos2",
                "<yellow>Coin 2</yellow>", "<gray>Utilise votre position actuelle.</gray>"));
        inventory.setItem(14, button(Material.MAP, "import",
                "<aqua>Importer la sélection</aqua>",
                selection.hasImporter()
                        ? "<gray>Depuis WorldEdit ou FAWE.</gray>"
                        : "<dark_gray>Aucun plugin de sélection détecté.</dark_gray>"));
        inventory.setItem(15, button(Material.CHEST, "save",
                "<gold>Enregistrer l'arène</gold>",
                "<gray>Valide et publie le brouillon.</gray>"));
        inventory.setItem(16, button(Material.BOOK, "reload",
                "<aqua>Recharger la configuration</aqua>",
                "<gray>Refusé si une partie est en cours.</gray>"));
        inventory.setItem(22, button(Material.COMPASS, "debug",
                "<white>Diagnostic</white>", "<gray>Compteurs internes.</gray>"));

        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String action, String name, String lore) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.displayName(services.messages().renderRaw(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(services.messages().renderRaw(lore).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(services.keys().guiAction(), PersistentDataType.STRING, action);
        });
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("dls.admin")) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(services.keys().guiAction(), PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        handle(player, action);
    }

    private void handle(Player player, String action) {
        PlayerId playerId = PlayerId.of(player.getUniqueId());
        switch (action) {
            case "prepare" -> {
                player.closeInventory();
                services.lifecycle().prepareAndStart(null, null).thenAccept(outcome ->
                        player.sendMessage(services.messages().render(outcome.messageKey(), outcome.placeholders())));
            }
            case "stop" -> {
                Commands.Outcome outcome = services.lifecycle().stop();
                player.sendMessage(services.messages().render(outcome.messageKey(), outcome.placeholders()));
            }
            case "pos1", "pos2" -> {
                int corner = "pos1".equals(action) ? 1 : 2;
                BlockPos position = new BlockPos(player.getLocation().getBlockX(),
                        player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                selection.setCorner(playerId, player.getWorld().getName(), corner, position);
                player.sendMessage(services.messages().render("admin.corner-set",
                        "corner", corner, "position", position.toString()));
            }
            case "import" -> {
                Optional<ArenaBounds> imported = selection.importExternal(playerId);
                player.sendMessage(imported
                        .map(bounds -> services.messages().render("admin.selection-ready",
                                "volume", bounds.volumeLong()))
                        .orElseGet(() -> services.messages().render("admin.selection-unavailable")));
            }
            case "save" -> saveArena(player, playerId);
            case "reload" -> {
                try {
                    boolean applied = services.lifecycle().reload(configuration.reload());
                    player.sendMessage(services.messages()
                            .render(applied ? "admin.reloaded" : "admin.phase-locked"));
                } catch (RuntimeException invalid) {
                    player.sendMessage(services.messages().render("admin.reload-failed",
                            "error", String.valueOf(invalid.getMessage())));
                }
            }
            case "debug" -> player.performCommand("dls admin debug");
            default -> { }
        }
    }

    private void saveArena(Player player, PlayerId playerId) {
        if (!services.lifecycle().phase().acceptsConfigurationChange()) {
            player.sendMessage(services.messages().render("admin.phase-locked"));
            return;
        }
        Optional<ArenaBounds> bounds = selection.bounds(playerId);
        if (bounds.isEmpty()) {
            player.sendMessage(services.messages().render("admin.selection-incomplete"));
            return;
        }
        try {
            configuration.saveArena(bounds.get());
            services.lifecycle().reload(configuration.reload());
            player.sendMessage(services.messages().render("admin.arena-saved",
                    "bounds", bounds.get().toString(), "volume", bounds.get().volumeLong()));
        } catch (RuntimeException invalid) {
            player.sendMessage(services.messages().render("admin.reload-failed",
                    "error", String.valueOf(invalid.getMessage())));
        }
    }

    /** Marqueur d'inventaire : identifie le menu de façon fiable, sans comparer un titre. */
    private static final class Holder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Composant vide utilisé si un libellé de {@code gui.yml} est absent. */
    static Component empty() {
        return Component.empty();
    }
}
