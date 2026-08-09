package io.github.youpl.diglikesteve.testsupport;

import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ordonnanceur déterministe : tout s'exécute immédiatement sur le thread appelant, et les
 * tâches différées sont mises en file pour être déclenchées explicitement par le test.
 *
 * <p>Il rend les scénarios reproductibles sans lancer de serveur Minecraft, conformément à
 * l'objectif du DAT §4.1.</p>
 */
public final class DirectSchedulerPort implements SchedulerPort {

    private final Deque<Runnable> delayed = new ArrayDeque<>();
    private final AtomicInteger globalCalls = new AtomicInteger();
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    @Override
    public void global(Runnable task) {
        globalCalls.incrementAndGet();
        task.run();
    }

    @Override
    public TaskHandle globalRepeating(Runnable task, long delayTicks, long periodTicks) {
        return new RecordedHandle();
    }

    @Override
    public TaskHandle globalDelayed(Runnable task, long delayTicks) {
        delayed.add(task);
        return new RecordedHandle();
    }

    @Override
    public void region(String worldName, int blockX, int blockZ, Runnable task) {
        task.run();
    }

    @Override
    public boolean entity(PlayerId playerId, Runnable task, Runnable retired) {
        task.run();
        return true;
    }

    @Override
    public void async(Runnable task) {
        task.run();
    }

    @Override
    public CompletableFuture<Void> cpu(Runnable task) {
        return run(task);
    }

    @Override
    public CompletableFuture<Void> database(Runnable task) {
        return run(task);
    }

    private CompletableFuture<Void> run(Runnable task) {
        try {
            task.run();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public boolean isPluginEnabled() {
        return enabled.get();
    }

    @Override
    public void shutdown() {
        enabled.set(false);
        delayed.clear();
    }

    /** Exécute toutes les tâches différées en attente, comme le ferait le passage des ticks. */
    public int runDelayed() {
        int executed = 0;
        while (!delayed.isEmpty()) {
            delayed.poll().run();
            executed++;
        }
        return executed;
    }

    public int globalCalls() {
        return globalCalls.get();
    }

    private static final class RecordedHandle implements TaskHandle {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
