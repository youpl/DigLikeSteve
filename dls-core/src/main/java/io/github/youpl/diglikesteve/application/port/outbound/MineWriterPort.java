package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.arena.BlockPalette;

import java.util.concurrent.CompletableFuture;

/**
 * Écriture progressive de la mine et gestion des tickets de chunks (DAT §6.3 et §15).
 *
 * <p>Le backend natif est la référence fonctionnelle et le seul chemin garanti sur Folia. Un
 * backend accélérateur tiers pourra implémenter cette même interface sur Paper.</p>
 */
public interface MineWriterPort {

    /** Pré-résout la palette en types de plateforme. Appelé une fois par préparation. */
    void bindPalette(BlockPalette palette);

    /** Charge le chunk et pose un ticket compté, libéré par {@link #releaseChunks()}. */
    CompletableFuture<Void> acquireChunk(String worldName, int chunkX, int chunkZ);

    /** Écrit un lot sur la région propriétaire. Le futur s'achève sur cette même région. */
    CompletableFuture<Void> writeBatch(String worldName, BlockBatch batch);

    /** Libère tous les tickets acquis. Idempotent. */
    void releaseChunks();

    /** Nombre de tickets encore détenus, exposé par {@code /dls admin debug}. */
    int heldChunkTickets();

    /** Vrai si le monde existe et est chargé. */
    boolean worldExists(String worldName);
}
