package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.reward.RewardDeliveryState;
import io.github.youpl.diglikesteve.core.reward.RewardOutboxEntry;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbox de récompense avec reprise après incident (DAT §12). */
public interface RewardOutboxRepository {

    /**
     * Insère l'entrée si sa clé d'idempotence est libre, et incrémente le compteur de victoires
     * dans la même transaction lorsque celui-ci est activé.
     *
     * @return faux si la clé existe déjà : la récompense a donc déjà été créée
     */
    boolean insertIfAbsent(RewardOutboxEntry entry, boolean incrementWins);

    List<RewardOutboxEntry> findByState(RewardDeliveryState state, int limit);

    List<RewardOutboxEntry> findPendingForPlayer(PlayerId playerId);

    Optional<RewardOutboxEntry> findById(UUID id);

    void updateState(UUID id, RewardDeliveryState state, int attempts, String lastError, long nowEpochMillis);

    int countByState(RewardDeliveryState state);
}
