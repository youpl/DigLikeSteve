package io.github.youpl.diglikesteve.application.service;

/**
 * Budget d'écriture progressive de la mine (DAT §6.3).
 *
 * @param maxBlocksPerBatch  nombre maximal de blocs écrits par lot, donc par passage sur une région
 * @param maxChunksInFlight  nombre maximal de chunks simultanément chargés et traités
 * @param maxVolume          volume d'arène refusé au-delà, avant toute allocation
 * @param maxHeapBytes       empreinte mémoire maximale estimée du plan
 */
public record MineWriteBudget(int maxBlocksPerBatch, int maxChunksInFlight, long maxVolume, long maxHeapBytes) {

    public MineWriteBudget {
        if (maxBlocksPerBatch <= 0) {
            throw new IllegalArgumentException("maxBlocksPerBatch doit être positif");
        }
        if (maxChunksInFlight <= 0) {
            throw new IllegalArgumentException("maxChunksInFlight doit être positif");
        }
        if (maxVolume <= 0L) {
            throw new IllegalArgumentException("maxVolume doit être positif");
        }
        if (maxHeapBytes <= 0L) {
            throw new IllegalArgumentException("maxHeapBytes doit être positif");
        }
    }

    public static MineWriteBudget defaults() {
        return new MineWriteBudget(4_096, 4, 2_000_000L, 256L * 1024L * 1024L);
    }
}
