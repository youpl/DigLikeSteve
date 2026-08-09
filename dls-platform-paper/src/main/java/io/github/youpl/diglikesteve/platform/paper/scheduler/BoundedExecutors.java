package io.github.youpl.diglikesteve.platform.paper.scheduler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exécuteurs privés et bornés (DAT §10.3).
 *
 * <p>Deux suffisent : un writer unique {@code dls-db-1} et un petit pool {@code dls-cpu-N}.
 * Les pools communs sont évités, les files sont bornées, les threads sont nommés et la
 * stratégie de rejet est explicite.</p>
 */
public final class BoundedExecutors {

    private final ThreadPoolExecutor database;
    private final ThreadPoolExecutor cpu;
    private final Logger logger;

    public BoundedExecutors(Logger logger, int cpuThreads, int queueCapacity) {
        this.logger = logger;
        this.database = build("dls-db", 1, queueCapacity, logger);
        this.cpu = build("dls-cpu", Math.max(1, Math.min(2, cpuThreads)), queueCapacity, logger);
    }

    private static ThreadPoolExecutor build(String prefix, int threads, int queueCapacity, Logger logger) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, error) ->
                    logger.log(Level.SEVERE, "Erreur non interceptée sur " + t.getName(), error));
            return thread;
        };
        RejectedExecutionHandler rejection = (runnable, executor) -> {
            if (!executor.isShutdown()) {
                logger.log(Level.SEVERE, "File " + prefix + " saturée : tâche rejetée");
            }
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, rejection);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public ThreadPoolExecutor database() {
        return database;
    }

    public ThreadPoolExecutor cpu() {
        return cpu;
    }

    public int databaseQueueSize() {
        return database.getQueue().size();
    }

    public int cpuQueueSize() {
        return cpu.getQueue().size();
    }

    /** Arrêt ordonné avec délai : le classloader doit pouvoir être déchargé (DAT §15). */
    public void shutdown(long timeoutSeconds) {
        shutdown(database, timeoutSeconds);
        shutdown(cpu, timeoutSeconds);
    }

    private void shutdown(ThreadPoolExecutor executor, long timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.warning("Exécuteur non terminé dans les délais, arrêt forcé");
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
