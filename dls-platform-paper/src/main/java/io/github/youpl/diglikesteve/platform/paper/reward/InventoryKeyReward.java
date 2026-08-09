package io.github.youpl.diglikesteve.platform.paper.reward;

import io.github.youpl.diglikesteve.application.port.outbound.RewardProviderPort;
import io.github.youpl.diglikesteve.core.reward.RewardOutboxEntry;
import io.github.youpl.diglikesteve.platform.paper.DlsKeys;
import io.github.youpl.diglikesteve.platform.paper.audience.MessageCatalog;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Remise d'un item de clé après restauration (DAT §12).
 *
 * <p>La clé est donnée sur l'{@code EntityScheduler} du gagnant, une fois son inventaire rendu.
 * Si le gagnant est hors ligne, l'entrée d'outbox reste en boîte aux lettres jusqu'à sa
 * prochaine connexion.</p>
 */
public final class InventoryKeyReward implements RewardProviderPort {

    private final Plugin plugin;
    private final DlsKeys keys;
    private final MessageCatalog messages;
    private final Material material;
    private final String displayName;

    public InventoryKeyReward(Plugin plugin, DlsKeys keys, MessageCatalog messages,
                              String materialName, String displayName) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.messages = Objects.requireNonNull(messages, "messages");
        Material resolved = Material.matchMaterial(materialName);
        this.material = resolved == null ? Material.TRIPWIRE_HOOK : resolved;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    @Override
    public String name() {
        return "inventory-key";
    }

    @Override
    public DeliverySemantics deliverySemantics() {
        // L'ajout d'item est confirmé sur le thread du joueur avant l'écriture de l'accusé.
        return DeliverySemantics.AT_LEAST_ONCE;
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return true;
    }

    @Override
    public CompletableFuture<Void> deliver(RewardOutboxEntry entry) {
        Player player = Bukkit.getPlayer(entry.playerId().value());
        if (player == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Gagnant hors ligne"));
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        player.getScheduler().execute(plugin, () -> {
            try {
                ItemStack key = ItemStack.of(material);
                key.editMeta(meta -> {
                    meta.displayName(messages.renderRaw(displayName).decoration(TextDecoration.ITALIC, false));
                    meta.getPersistentDataContainer().set(keys.rewardKey(), PersistentDataType.STRING,
                            entry.idempotencyKey());
                });
                // L'inventaire du gagnant a déjà été restauré : addItem est ici volontaire.
                player.getInventory().addItem(key).values()
                        .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                done.complete(null);
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        }, () -> done.completeExceptionally(new IllegalStateException("Joueur retiré")), 1L);
        return done;
    }
}
