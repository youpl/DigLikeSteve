package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.reward.RewardOutboxEntry;

import java.util.concurrent.CompletableFuture;

/**
 * Fournisseur de récompense (DAT §12).
 *
 * <p>Une commande console arbitraire ne peut pas garantir « exactement une fois » si le serveur
 * tombe entre l'exécution et l'accusé de réception : l'adaptateur annonce donc explicitement sa
 * sémantique via {@link #deliverySemantics()}.</p>
 */
public interface RewardProviderPort {

    String name();

    DeliverySemantics deliverySemantics();

    /** Le gagnant doit-il être en ligne pour recevoir la récompense ? */
    boolean requiresOnlinePlayer();

    /** Remet la récompense. Le futur échoue si le fournisseur a refusé ou levé une erreur. */
    CompletableFuture<Void> deliver(RewardOutboxEntry entry);

    enum DeliverySemantics {
        AT_LEAST_ONCE,
        EXACTLY_ONCE
    }
}
