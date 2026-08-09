package io.github.youpl.diglikesteve.core.player;

import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.core.shared.SessionId;

import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Snapshot opaque d'un inventaire (DAT §8.1).
 *
 * <p>Le domaine ne sait pas décoder {@code payload} : la sérialisation binaire versionnée est
 * l'affaire de l'adaptateur Paper. Le domaine garantit en revanche l'intégrité par checksum et
 * pilote la machine à états {@link CustodyState}.</p>
 */
public final class InventorySnapshot {

    private final UUID id;
    private final PlayerId playerId;
    private final SessionId sessionId;
    private final byte[] payload;
    private final long checksum;
    private final int formatVersion;
    private final CustodyState state;
    private final long capturedAtEpochMillis;
    private final long updatedAtEpochMillis;

    public InventorySnapshot(
            UUID id,
            PlayerId playerId,
            SessionId sessionId,
            byte[] payload,
            long checksum,
            int formatVersion,
            CustodyState state,
            long capturedAtEpochMillis,
            long updatedAtEpochMillis) {
        this.id = Objects.requireNonNull(id, "id");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        this.checksum = checksum;
        this.formatVersion = formatVersion;
        this.state = Objects.requireNonNull(state, "state");
        this.capturedAtEpochMillis = capturedAtEpochMillis;
        this.updatedAtEpochMillis = updatedAtEpochMillis;
    }

    /** Crée un snapshot fraîchement capturé, checksum calculé. */
    public static InventorySnapshot captured(
            PlayerId playerId, SessionId sessionId, byte[] payload, int formatVersion, long nowEpochMillis) {
        return new InventorySnapshot(UUID.randomUUID(), playerId, sessionId, payload,
                checksumOf(payload), formatVersion, CustodyState.CAPTURED, nowEpochMillis, nowEpochMillis);
    }

    public static long checksumOf(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        return crc.getValue();
    }

    /** Détecte un snapshot tronqué ou corrompu avant toute restauration. */
    public boolean isIntact() {
        return payload.length > 0 && checksumOf(payload) == checksum;
    }

    public InventorySnapshot withState(CustodyState newState, long nowEpochMillis) {
        return new InventorySnapshot(id, playerId, sessionId, payload, checksum, formatVersion,
                newState, capturedAtEpochMillis, nowEpochMillis);
    }

    public UUID id() {
        return id;
    }

    public PlayerId playerId() {
        return playerId;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    public long checksum() {
        return checksum;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public CustodyState state() {
        return state;
    }

    public long capturedAtEpochMillis() {
        return capturedAtEpochMillis;
    }

    public long updatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    @Override
    public String toString() {
        // Le contenu de l'inventaire n'est jamais journalisé (DAT §19).
        return "InventorySnapshot[" + id + " player=" + playerId + " state=" + state
                + " bytes=" + payload.length + "]";
    }
}
