package io.github.youpl.diglikesteve.core.arena;

import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Arène préparée : cuboïde, plan de génération, index anti-double-score et poches de spawn.
 * Les slots sont réservés par {@code compare-and-set} : deux joueurs ne peuvent jamais être
 * téléportés au même endroit (DAT §6.4).
 */
public final class Arena {

    private final MinePlan plan;
    private final ConsumedIndex consumed;
    private final List<SpawnSlot> slots;
    private final AtomicReferenceArray<PlayerId> slotOwners;

    public Arena(MinePlan plan, ConsumedIndex consumed, List<SpawnSlot> slots) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.consumed = Objects.requireNonNull(consumed, "consumed");
        this.slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        this.slotOwners = new AtomicReferenceArray<>(this.slots.size());
    }

    public ArenaBounds bounds() {
        return plan.bounds();
    }

    public MinePlan plan() {
        return plan;
    }

    public ConsumedIndex consumed() {
        return consumed;
    }

    public List<SpawnSlot> slots() {
        return slots;
    }

    public int capacity() {
        return slots.size();
    }

    /** Réserve un slot libre. Retourne vide si l'arène est pleine. */
    public Optional<SpawnSlot> reserveSlot(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        for (int i = 0; i < slotOwners.length(); i++) {
            if (playerId.equals(slotOwners.get(i))) {
                return Optional.of(slots.get(i));
            }
        }
        for (int i = 0; i < slotOwners.length(); i++) {
            if (slotOwners.compareAndSet(i, null, playerId)) {
                return Optional.of(slots.get(i));
            }
        }
        return Optional.empty();
    }

    public Optional<SpawnSlot> slotOf(PlayerId playerId) {
        for (int i = 0; i < slotOwners.length(); i++) {
            if (playerId.equals(slotOwners.get(i))) {
                return Optional.of(slots.get(i));
            }
        }
        return Optional.empty();
    }

    public void releaseSlot(PlayerId playerId) {
        for (int i = 0; i < slotOwners.length(); i++) {
            if (playerId.equals(slotOwners.get(i))) {
                slotOwners.compareAndSet(i, playerId, null);
                return;
            }
        }
    }

    public int reservedSlots() {
        int count = 0;
        for (int i = 0; i < slotOwners.length(); i++) {
            if (slotOwners.get(i) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Valeur d'une position si elle vient d'être consommée.
     *
     * @return les points à créditer, ou {@code 0} si la position est hors arène, déjà payée,
     *         vidée pour un spawn ou non scorable
     */
    public int consumeForPoints(int x, int y, int z) {
        int index = bounds().index(x, y, z);
        if (index < 0) {
            return 0;
        }
        return consumeIndexForPoints(index);
    }

    public int consumeForPoints(BlockPos pos) {
        return consumeForPoints(pos.x(), pos.y(), pos.z());
    }

    public int consumeIndexForPoints(int index) {
        if (!consumed.tryConsume(index)) {
            return 0;
        }
        return plan.pointsAt(index);
    }
}
