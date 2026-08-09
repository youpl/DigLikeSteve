package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.player.InventorySnapshot;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.concurrent.CompletableFuture;

/**
 * Capture et restauration réelles de l'état d'un joueur (DAT §8.1).
 *
 * <p>La capture ferme l'inventaire ouvert, clone puis sérialise ; elle ne modifie rien. La
 * restauration écrit des slots exacts avec {@code setContents} et jamais {@code addItem}, ce
 * qui interdit les doublons et les débordements au sol.</p>
 */
public interface InventoryCustodyPort {

    /** Sérialise l'état complet du joueur sans y toucher. */
    CompletableFuture<InventorySnapshot> capture(PlayerId playerId, SessionId sessionId);

    /** Vide l'inventaire. À n'appeler qu'après persistance confirmée du snapshot. */
    CompletableFuture<Boolean> clearInventory(PlayerId playerId);

    /** Réapplique exactement tous les champs du snapshot. */
    CompletableFuture<Boolean> restore(PlayerId playerId, InventorySnapshot snapshot);

    /** Version du format binaire produit par cet adaptateur. */
    int formatVersion();
}
