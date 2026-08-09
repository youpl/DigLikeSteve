package io.github.youpl.diglikesteve.platform.paper.inventory;

import io.github.youpl.diglikesteve.application.port.outbound.InventoryCustodyPort;
import io.github.youpl.diglikesteve.core.player.InventorySnapshot;
import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Capture et restauration réelles (DAT §8.1). Chaque opération est exécutée sur
 * l'{@code EntityScheduler} du joueur concerné : c'est le seul thread autorisé à toucher son
 * inventaire, sur Paper comme sur Folia.
 */
public final class PaperInventoryCustodyAdapter implements InventoryCustodyPort {

    private final Plugin plugin;
    private final KitFactory kits;
    private final Clock clock;

    public PaperInventoryCustodyAdapter(Plugin plugin, KitFactory kits, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.kits = Objects.requireNonNull(kits, "kits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<InventorySnapshot> capture(PlayerId playerId, SessionId sessionId) {
        return onPlayer(playerId, player -> {
            // Fermer l'inventaire ouvert évite de capturer un état à moitié déplacé.
            player.closeInventory();
            byte[] payload = PlayerStateCodec.encode(player);
            return InventorySnapshot.captured(playerId, sessionId, payload,
                    PlayerStateCodec.FORMAT_VERSION, clock.epochMillis());
        });
    }

    @Override
    public CompletableFuture<Boolean> clearInventory(PlayerId playerId) {
        return onPlayer(playerId, player -> {
            player.closeInventory();
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setExtraContents(null);
            player.setItemOnCursor(null);
            player.setLevel(0);
            player.setExp(0.0f);
            player.setTotalExperience(0);
            player.clearActivePotionEffects();
            player.setFoodLevel(20);
            player.setSaturation(10.0f);
            return Boolean.TRUE;
        });
    }

    @Override
    public CompletableFuture<Boolean> restore(PlayerId playerId, InventorySnapshot snapshot) {
        if (!snapshot.isIntact()) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return onPlayer(playerId, player -> {
            PlayerStateCodec.decodeAndApply(player, snapshot.payload());
            return Boolean.TRUE;
        });
    }

    @Override
    public int formatVersion() {
        return PlayerStateCodec.FORMAT_VERSION;
    }

    /** Planifie une action sur l'{@code EntityScheduler} du joueur et en rapporte le résultat. */
    private <T> CompletableFuture<T> onPlayer(PlayerId playerId, Function<Player, T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Player player = Bukkit.getPlayer(playerId.value());
        if (player == null) {
            future.completeExceptionally(new IllegalStateException("Joueur hors ligne : " + playerId));
            return future;
        }
        boolean scheduled = player.getScheduler().execute(plugin, () -> {
            try {
                future.complete(action.apply(player));
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        }, () -> future.completeExceptionally(new IllegalStateException("Joueur retiré : " + playerId)), 1L);

        if (!scheduled) {
            future.completeExceptionally(new IllegalStateException("Impossible de planifier sur " + playerId));
        }
        return future;
    }

    /** Supprime tout item marqué DLS, quel que soit son slot (DAT §9). */
    public CompletableFuture<Boolean> stripSessionItems(PlayerId playerId) {
        return onPlayer(playerId, player -> {
            var inventory = player.getInventory();
            var contents = inventory.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (kits.belongsToSession(contents[slot])) {
                    inventory.setItem(slot, null);
                }
            }
            if (kits.belongsToSession(player.getItemOnCursor())) {
                player.setItemOnCursor(null);
            }
            return Boolean.TRUE;
        });
    }

    public CompletableFuture<Boolean> applyKit(PlayerId playerId, SessionId sessionId) {
        return onPlayer(playerId, player -> {
            kits.apply(player.getInventory(), sessionId);
            return Boolean.TRUE;
        });
    }
}
