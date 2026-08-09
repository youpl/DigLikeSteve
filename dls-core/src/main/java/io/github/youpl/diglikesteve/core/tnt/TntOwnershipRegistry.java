package io.github.youpl.diglikesteve.core.tnt;

import io.github.youpl.diglikesteve.core.shared.Clock;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Registre court terme de propriété des TNT amorcées (DAT §7.2).
 *
 * <p>{@code TNTPrimed#getSource()} peut devenir nul après un déchargement de chunk : le PDC posé
 * sur l'entité et ce registre à durée de vie limitée donnent une attribution stable. Le registre
 * est purgé périodiquement pour ne pas retenir en mémoire des TNT disparues (DAT §15).</p>
 */
public final class TntOwnershipRegistry {

    private final ConcurrentHashMap<UUID, Entry> owners = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long ttlNanos;

    public TntOwnershipRegistry(Clock clock, long ttlSeconds) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttlNanos = TimeUnit.SECONDS.toNanos(ttlSeconds);
    }

    public void register(UUID tntId, PlayerId owner) {
        Objects.requireNonNull(tntId, "tntId");
        Objects.requireNonNull(owner, "owner");
        owners.put(tntId, new Entry(owner, clock.nanoTime() + ttlNanos));
    }

    /** Propage l'identité d'une TNT source vers une TNT amorcée par réaction en chaîne. */
    public void propagate(UUID sourceTntId, UUID chainedTntId) {
        lookup(sourceTntId).ifPresent(owner -> register(chainedTntId, owner));
    }

    public Optional<PlayerId> lookup(UUID tntId) {
        Entry entry = owners.get(tntId);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.nanoTime() - entry.expiresAtNanos >= 0L) {
            owners.remove(tntId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.owner);
    }

    public void forget(UUID tntId) {
        owners.remove(tntId);
    }

    /** Purge des entrées expirées. Appelée par la tâche de maintenance globale. */
    public int purgeExpired() {
        long now = clock.nanoTime();
        int removed = 0;
        Iterator<Map.Entry<UUID, Entry>> iterator = owners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> entry = iterator.next();
            if (now - entry.getValue().expiresAtNanos >= 0L) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return owners.size();
    }

    public void clear() {
        owners.clear();
    }

    private record Entry(PlayerId owner, long expiresAtNanos) {
    }
}
