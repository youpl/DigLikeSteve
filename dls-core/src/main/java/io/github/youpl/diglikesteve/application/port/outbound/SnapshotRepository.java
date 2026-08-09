package io.github.youpl.diglikesteve.application.port.outbound;

import io.github.youpl.diglikesteve.core.player.CustodyState;
import io.github.youpl.diglikesteve.core.player.InventorySnapshot;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance des gardes d'inventaire (DAT §11).
 *
 * <p>Une contrainte d'unicité en base garantit qu'un joueur ne peut pas avoir deux gardes
 * actives simultanées ; {@link #compareAndSetState} assure des transitions atomiques.</p>
 */
public interface SnapshotRepository {

    /**
     * Insère un snapshot en état {@code CAPTURED} dans une transaction durable.
     *
     * @return faux si une garde active existe déjà pour ce joueur
     */
    boolean insertCaptured(InventorySnapshot snapshot);

    /**
     * Transition atomique de l'état persisté.
     *
     * @return vrai si l'état attendu était bien celui en base et a été remplacé
     */
    boolean compareAndSetState(UUID snapshotId, CustodyState expected, CustodyState target, long nowEpochMillis);

    /**
     * Réclame une garde pour restauration : passe en {@code RESTORE_PENDING} depuis tout état
     * non terminal, en une seule instruction atomique.
     *
     * @return faux si la garde est déjà terminale — la restauration a donc déjà eu lieu
     */
    boolean claimForRestore(UUID snapshotId, long nowEpochMillis);

    Optional<InventorySnapshot> findById(UUID snapshotId);

    /** Garde non terminale d'un joueur, s'il en existe une. */
    Optional<InventorySnapshot> findActiveByPlayer(PlayerId playerId);

    /** Snapshots à restaurer obligatoirement au démarrage (DAT §8.5). */
    List<InventorySnapshot> findRequiringRecovery();

    /** Marque toutes les gardes non terminales en {@code RESTORE_PENDING}, en une transaction. */
    int markAllPendingRestore(long nowEpochMillis);

    /** Purge les snapshots restaurés plus anciens que la rétention configurée. */
    int purgeRestoredOlderThan(long epochMillis, int batchSize);

    int countRequiringRecovery();
}
