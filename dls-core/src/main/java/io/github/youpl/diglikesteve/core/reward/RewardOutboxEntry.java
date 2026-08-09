package io.github.youpl.diglikesteve.core.reward;

import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.Objects;
import java.util.UUID;

/**
 * Ligne d'outbox (DAT §12).
 *
 * <p>{@code idempotencyKey} combine la session et le joueur : il est unique en base, ce qui
 * interdit structurellement une double remise pour une même partie. La sémantique annoncée
 * reste néanmoins <em>at-least-once</em> dès qu'une commande console arbitraire est utilisée.</p>
 */
public record RewardOutboxEntry(
        UUID id,
        SessionId sessionId,
        PlayerId playerId,
        String idempotencyKey,
        String strategy,
        String payload,
        RewardDeliveryState state,
        int attempts,
        long createdAtEpochMillis,
        long updatedAtEpochMillis,
        String lastError) {

    public RewardOutboxEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(state, "state");
    }

    public static String idempotencyKeyFor(SessionId sessionId, PlayerId playerId) {
        return sessionId.value() + ":" + playerId.value();
    }

    public RewardOutboxEntry withState(RewardDeliveryState newState, long nowEpochMillis, String error) {
        return new RewardOutboxEntry(id, sessionId, playerId, idempotencyKey, strategy, payload,
                newState, attempts, createdAtEpochMillis, nowEpochMillis, error);
    }

    public RewardOutboxEntry withAttempt(long nowEpochMillis) {
        return new RewardOutboxEntry(id, sessionId, playerId, idempotencyKey, strategy, payload,
                state, attempts + 1, createdAtEpochMillis, nowEpochMillis, lastError);
    }
}
