package io.github.youpl.diglikesteve.application.service;

import io.github.youpl.diglikesteve.application.port.outbound.BlockBatch;
import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.application.port.outbound.MineWriterPort;
import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.core.arena.Arena;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.arena.BlockPalette;
import io.github.youpl.diglikesteve.core.arena.ConsumedIndex;
import io.github.youpl.diglikesteve.core.arena.MinePlan;
import io.github.youpl.diglikesteve.core.arena.MinePlanFactory;
import io.github.youpl.diglikesteve.core.arena.SpawnSlot;
import io.github.youpl.diglikesteve.core.arena.SpawnSlotPlanner;
import io.github.youpl.diglikesteve.core.arena.SpawnSlotSettings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Préparation de la mine (DAT §6.2 et §6.3).
 *
 * <p>Le calcul du plan est pur et tourne sur l'exécuteur CPU privé. L'application aux chunks ne
 * l'est pas : chaque lot est confié à la région propriétaire de ses positions, en respectant un
 * budget de blocs par lot et un nombre maximal de chunks simultanés.</p>
 */
public final class MinePreparationService {

    private final SchedulerPort schedulers;
    private final MineWriterPort writer;
    private final LogPort log;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong blocksWritten = new AtomicLong();
    private final AtomicLong totalBlocks = new AtomicLong();

    public MinePreparationService(SchedulerPort schedulers, MineWriterPort writer, LogPort log) {
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.log = Objects.requireNonNull(log, "log");
    }

    public boolean isRunning() {
        return running.get();
    }

    public long blocksWritten() {
        return blocksWritten.get();
    }

    public long totalBlocks() {
        return totalBlocks.get();
    }

    /** Progression de 0 à 100. */
    public int progressPercent() {
        long total = totalBlocks.get();
        return total <= 0L ? 0 : (int) Math.min(100L, blocksWritten.get() * 100L / total);
    }

    /**
     * Vérifie les limites, calcule le plan puis remplit la zone.
     *
     * @return l'arène prête ; le futur échoue si une limite est dépassée ou si un lot échoue
     */
    public CompletableFuture<Arena> prepare(
            ArenaBounds bounds,
            BlockPalette palette,
            boolean[] hazardous,
            SpawnSlotSettings slotSettings,
            long seed,
            MineWriteBudget budget) {

        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Une préparation est déjà en cours"));
        }
        blocksWritten.set(0L);
        totalBlocks.set(0L);

        CompletableFuture<Arena> result = new CompletableFuture<>();
        try {
            validate(bounds, budget);
        } catch (RuntimeException failure) {
            running.set(false);
            return CompletableFuture.failedFuture(failure);
        }

        schedulers.cpu(() -> {
            MinePlan plan = MinePlanFactory.generate(bounds, palette, seed);
            ConsumedIndex consumed = new ConsumedIndex(plan.volume());
            List<SpawnSlot> slots = SpawnSlotPlanner.plan(plan, consumed, hazardous, slotSettings, seed);
            Arena arena = new Arena(plan, consumed, slots);
            totalBlocks.set(plan.volume());
            log.info("Plan de mine calculé", "volume", plan.volume(), "slots", slots.size(), "seed", seed);
            // Le calcul est pur, mais tout ce qui suit touche l'API du serveur : on repasse
            // sur la région globale avant la moindre résolution de matériau ou de chunk.
            schedulers.global(() -> {
                writer.bindPalette(palette);
                writeProgressively(arena, budget, result);
            });
        }).exceptionally(failure -> {
            fail(result, failure);
            return null;
        });

        return result.whenComplete((arena, failure) -> running.set(false));
    }

    private void validate(ArenaBounds bounds, MineWriteBudget budget) {
        long volume = bounds.volumeLong();
        if (volume <= 0L) {
            throw new IllegalArgumentException("Sélection vide");
        }
        if (volume > budget.maxVolume() || volume > ArenaBounds.ABSOLUTE_MAX_VOLUME) {
            throw new IllegalArgumentException("Volume refusé : " + volume + " > " + budget.maxVolume());
        }
        long estimated = MinePlan.estimatedBytes(volume);
        if (estimated > budget.maxHeapBytes()) {
            throw new IllegalArgumentException("Plan trop volumineux : " + estimated + " octets estimés");
        }
        if (!writer.worldExists(bounds.worldName())) {
            throw new IllegalArgumentException("Monde introuvable : " + bounds.worldName());
        }
    }

    /** Découpe la mine en chunks, puis chaque chunk en lots bornés. */
    private void writeProgressively(Arena arena, MineWriteBudget budget, CompletableFuture<Arena> result) {
        ArenaBounds bounds = arena.bounds();
        Deque<int[]> chunks = new ArrayDeque<>(bounds.chunkCount());
        for (int cx = bounds.minChunkX(); cx <= bounds.maxChunkX(); cx++) {
            for (int cz = bounds.minChunkZ(); cz <= bounds.maxChunkZ(); cz++) {
                chunks.add(new int[] {cx, cz});
            }
        }

        int lanes = Math.min(budget.maxChunksInFlight(), Math.max(1, chunks.size()));
        AtomicLong remainingLanes = new AtomicLong(lanes);
        for (int lane = 0; lane < lanes; lane++) {
            pumpNextChunk(arena, budget, chunks, result, remainingLanes);
        }
    }

    private void pumpNextChunk(
            Arena arena,
            MineWriteBudget budget,
            Deque<int[]> chunks,
            CompletableFuture<Arena> result,
            AtomicLong remainingLanes) {

        if (result.isDone()) {
            return;
        }
        int[] chunk;
        synchronized (chunks) {
            chunk = chunks.poll();
        }
        if (chunk == null) {
            if (remainingLanes.decrementAndGet() == 0L) {
                result.complete(arena);
            }
            return;
        }
        if (!schedulers.isPluginEnabled()) {
            fail(result, new IllegalStateException("Plugin désactivé pendant la préparation"));
            return;
        }

        String worldName = arena.bounds().worldName();
        writer.acquireChunk(worldName, chunk[0], chunk[1])
                .thenCompose(ignored -> writeChunk(arena, budget, chunk[0], chunk[1]))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        log.error("Échec d'écriture de chunk", failure, "chunkX", chunk[0], "chunkZ", chunk[1]);
                        fail(result, failure);
                        return;
                    }
                    pumpNextChunk(arena, budget, chunks, result, remainingLanes);
                });
    }

    private CompletableFuture<Void> writeChunk(Arena arena, MineWriteBudget budget, int chunkX, int chunkZ) {
        List<BlockBatch> batches = buildBatches(arena, budget, chunkX, chunkZ);
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        String worldName = arena.bounds().worldName();
        for (BlockBatch batch : batches) {
            chain = chain.thenCompose(ignored -> writer.writeBatch(worldName, batch)
                    .thenRun(() -> blocksWritten.addAndGet(batch.length())));
        }
        return chain;
    }

    /** Construit les lots d'un chunk, en ignorant les positions vidées pour un spawn. */
    private List<BlockBatch> buildBatches(Arena arena, MineWriteBudget budget, int chunkX, int chunkZ) {
        ArenaBounds bounds = arena.bounds();
        MinePlan plan = arena.plan();

        int fromX = Math.max(bounds.minX(), chunkX << 4);
        int toX = Math.min(bounds.maxX(), (chunkX << 4) + 15);
        int fromZ = Math.max(bounds.minZ(), chunkZ << 4);
        int toZ = Math.min(bounds.maxZ(), (chunkZ << 4) + 15);

        List<BlockBatch> batches = new ArrayList<>();
        int capacity = budget.maxBlocksPerBatch();
        int[] indices = new int[capacity];
        byte[] ids = new byte[capacity];
        int cursor = 0;

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    int index = bounds.index(x, y, z);
                    indices[cursor] = index;
                    ids[cursor] = plan.typeAt(index);
                    cursor++;
                    if (cursor == capacity) {
                        batches.add(new BlockBatch(bounds, chunkX, chunkZ, indices, ids, cursor));
                        indices = new int[capacity];
                        ids = new byte[capacity];
                        cursor = 0;
                    }
                }
            }
        }
        if (cursor > 0) {
            batches.add(new BlockBatch(bounds, chunkX, chunkZ, indices, ids, cursor));
        }
        return batches;
    }

    private void fail(CompletableFuture<Arena> result, Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        result.completeExceptionally(cause);
        writer.releaseChunks();
    }

    /** Libère les tickets de chunks. Idempotent, appelé au nettoyage de session. */
    public void release() {
        writer.releaseChunks();
    }
}
