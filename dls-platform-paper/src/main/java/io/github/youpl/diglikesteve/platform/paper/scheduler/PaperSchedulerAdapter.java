package io.github.youpl.diglikesteve.platform.paper.scheduler;

import io.github.youpl.diglikesteve.application.port.outbound.SchedulerPort;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstraction unifiée des schedulers Paper et Folia (DAT §3 et §22, lot 1).
 *
 * <p>Les API {@code GlobalRegionScheduler}, {@code RegionScheduler}, {@code EntityScheduler} et
 * {@code AsyncScheduler} existent aussi sur Paper : le même code fonctionne donc sur les deux
 * plateformes, sans branchement conditionnel dans la logique métier.</p>
 *
 * <p>Tous les handles de tâches sont conservés afin d'être annulés au nettoyage (DAT §15).</p>
 */
public final class PaperSchedulerAdapter implements SchedulerPort {

    private final Plugin plugin;
    private final BoundedExecutors executors;
    private final Set<PaperTaskHandle> handles = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    public PaperSchedulerAdapter(Plugin plugin, BoundedExecutors executors) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    @Override
    public void global(Runnable task) {
        if (!isPluginEnabled()) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, wrap(task));
    }

    @Override
    public TaskHandle globalRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (!isPluginEnabled()) {
            return PaperTaskHandle.cancelled();
        }
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, ignored -> wrap(task).run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return track(scheduled);
    }

    @Override
    public TaskHandle globalDelayed(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) {
            return PaperTaskHandle.cancelled();
        }
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, ignored -> wrap(task).run(), Math.max(1L, delayTicks));
        return track(scheduled);
    }

    @Override
    public void region(String worldName, int blockX, int blockZ, Runnable task) {
        if (!isPluginEnabled()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        // Le RegionScheduler attend des coordonnées de chunk.
        Bukkit.getRegionScheduler().execute(plugin, world, blockX >> 4, blockZ >> 4, wrap(task));
    }

    @Override
    public boolean entity(PlayerId playerId, Runnable task, Runnable retired) {
        if (!isPluginEnabled()) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId.value());
        if (player == null) {
            if (retired != null) {
                retired.run();
            }
            return false;
        }
        return player.getScheduler().execute(plugin, wrap(task), retired, 1L);
    }

    @Override
    public void async(Runnable task) {
        if (!isPluginEnabled()) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> wrap(task).run());
    }

    @Override
    public CompletableFuture<Void> cpu(Runnable task) {
        return submit(task, true);
    }

    @Override
    public CompletableFuture<Void> database(Runnable task) {
        return submit(task, false);
    }

    private CompletableFuture<Void> submit(Runnable task, boolean cpuPool) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (shuttingDown.get()) {
            future.completeExceptionally(new IllegalStateException("Plugin en cours d'arrêt"));
            return future;
        }
        Runnable wrapped = () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        };
        try {
            (cpuPool ? executors.cpu() : executors.database()).execute(wrapped);
        } catch (RuntimeException rejected) {
            future.completeExceptionally(rejected);
        }
        return future;
    }

    @Override
    public boolean isPluginEnabled() {
        return !shuttingDown.get() && plugin.isEnabled();
    }

    @Override
    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        handles.forEach(PaperTaskHandle::cancel);
        handles.clear();
        try {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } catch (RuntimeException ignored) {
            // Le serveur peut déjà être en cours d'extinction.
        }
        executors.shutdown(5L);
    }

    /** Toute continuation vérifie que le plugin est encore actif (DAT §15). */
    private Runnable wrap(Runnable task) {
        return () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            try {
                task.run();
            } catch (Throwable failure) {
                plugin.getLogger().severe("Erreur dans une tâche DLS : " + failure);
                failure.printStackTrace();
            }
        };
    }

    private TaskHandle track(ScheduledTask scheduled) {
        PaperTaskHandle handle = new PaperTaskHandle(scheduled);
        handles.add(handle);
        return handle;
    }

    public int trackedTasks() {
        handles.removeIf(PaperTaskHandle::isCancelled);
        return handles.size();
    }

    public BoundedExecutors executors() {
        return executors;
    }

    private static final class PaperTaskHandle implements TaskHandle {

        private final ScheduledTask task;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private PaperTaskHandle(ScheduledTask task) {
            this.task = task;
        }

        static PaperTaskHandle cancelled() {
            PaperTaskHandle handle = new PaperTaskHandle(null);
            handle.cancelled.set(true);
            return handle;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true) && task != null) {
                task.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || (task != null && task.isCancelled());
        }
    }

    /** Exécute une action sur le thread propriétaire d'une entité, avec délai borné. */
    public boolean entityDelayed(PlayerId playerId, Runnable task, long delayTicks) {
        Player player = Bukkit.getPlayer(playerId.value());
        if (player == null) {
            return false;
        }
        return player.getScheduler().runDelayed(plugin, ignored -> wrap(task).run(), null,
                Math.max(1L, delayTicks)) != null;
    }

    /** Utilitaire de conversion secondes vers ticks. */
    public static long ticks(long seconds) {
        return TimeUnit.SECONDS.toMillis(seconds) / 50L;
    }
}
