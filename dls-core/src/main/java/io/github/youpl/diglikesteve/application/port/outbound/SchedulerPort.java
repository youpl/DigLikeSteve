package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction unifiée des schedulers Paper et Folia (DAT §3 et §22, lot 1).
 *
 * <p>Sur Folia il n'existe pas de « main thread » unique : chaque appel doit s'exécuter sur le
 * thread propriétaire de la région concernée. Cette interface impose donc de nommer
 * explicitement le contexte d'exécution ; l'adaptateur route vers le bon scheduler.</p>
 */
public interface SchedulerPort {

    /** Exécute sur la région globale : état du mini-jeu, annonces, commandes console. */
    void global(Runnable task);

    /** Tâche périodique globale. Le handle doit être conservé pour l'annulation au nettoyage. */
    TaskHandle globalRepeating(Runnable task, long delayTicks, long periodTicks);

    /** Tâche globale différée, utilisée par le règlement retardé des explosions. */
    TaskHandle globalDelayed(Runnable task, long delayTicks);

    /** Exécute sur la région propriétaire d'une position : lecture ou écriture de blocs. */
    void region(String worldName, int blockX, int blockZ, Runnable task);

    /**
     * Exécute sur l'{@code EntityScheduler} d'un joueur : inventaire, état, téléportation.
     *
     * @param retired exécuté si le joueur n'existe plus ; ne doit pas toucher l'API Bukkit
     * @return vrai si la tâche a pu être planifiée
     */
    boolean entity(PlayerId playerId, Runnable task, Runnable retired);

    /** Exécute hors thread de région : calculs purs et entrées/sorties uniquement. */
    void async(Runnable task);

    /** Exécuteur CPU privé et borné, pour la génération du plan et le tri du classement. */
    CompletableFuture<Void> cpu(Runnable task);

    /** Writer unique de la base de données. */
    CompletableFuture<Void> database(Runnable task);

    /** Vrai tant que le plugin est actif : toute continuation tardive doit le vérifier. */
    boolean isPluginEnabled();

    /** Annule toutes les tâches connues et arrête les exécuteurs privés. */
    void shutdown();

    /** Poignée annulable d'une tâche planifiée. */
    interface TaskHandle {
        void cancel();

        boolean isCancelled();
    }
}
