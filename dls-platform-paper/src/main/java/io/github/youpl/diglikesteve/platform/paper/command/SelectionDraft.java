package io.github.youpl.diglikesteve.platform.paper.command;

import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Brouillon de sélection d'arène (DAT §6.1).
 *
 * <p>L'interface d'administration modifie ce brouillon ; la validation et l'écriture dans la
 * configuration sont atomiques et n'ont lieu qu'en phase {@code IDLE}.</p>
 *
 * <p>L'import externe passe par un {@link SelectionImporter} facultatif : sans WorldEdit ni
 * FAWE, le mode natif par coordonnées reste le chemin garanti.</p>
 */
public final class SelectionDraft {

    private final Map<PlayerId, Corner> firstCorners = new ConcurrentHashMap<>();
    private final Map<PlayerId, Corner> secondCorners = new ConcurrentHashMap<>();
    private final Set<String> pendingConfirmations = ConcurrentHashMap.newKeySet();
    private final AtomicReference<SelectionImporter> importer = new AtomicReference<>();

    /** Branche un importateur de sélection tiers, s'il a été détecté au démarrage. */
    public void importer(SelectionImporter external) {
        importer.set(external);
    }

    public boolean hasImporter() {
        return importer.get() != null;
    }

    public void setCorner(PlayerId playerId, String worldName, int corner, BlockPos position) {
        Corner value = new Corner(worldName, position);
        if (corner == 1) {
            firstCorners.put(playerId, value);
        } else {
            secondCorners.put(playerId, value);
        }
    }

    /** Bornes normalisées si les deux coins sont posés dans le même monde. */
    public Optional<ArenaBounds> bounds(PlayerId playerId) {
        Corner first = firstCorners.get(playerId);
        Corner second = secondCorners.get(playerId);
        if (first == null || second == null || !first.worldName().equals(second.worldName())) {
            return Optional.empty();
        }
        return Optional.of(ArenaBounds.of(first.worldName(), first.position(), second.position()));
    }

    /** Importe la sélection courante du joueur depuis WorldEdit ou FAWE. */
    public Optional<ArenaBounds> importExternal(PlayerId playerId) {
        SelectionImporter external = importer.get();
        if (external == null) {
            return Optional.empty();
        }
        Optional<ArenaBounds> imported = external.selectionOf(playerId);
        imported.ifPresent(bounds -> {
            firstCorners.put(playerId, new Corner(bounds.worldName(),
                    new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())));
            secondCorners.put(playerId, new Corner(bounds.worldName(),
                    new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ())));
        });
        return imported;
    }

    public void clear(PlayerId playerId) {
        firstCorners.remove(playerId);
        secondCorners.remove(playerId);
    }

    public void requestConfirmation(String senderName) {
        pendingConfirmations.add(senderName);
    }

    public boolean isConfirmed(String senderName) {
        return pendingConfirmations.contains(senderName);
    }

    public void clearConfirmation(String senderName) {
        pendingConfirmations.remove(senderName);
    }

    private record Corner(String worldName, BlockPos position) {
    }

    /** Point d'extension implémenté par l'adaptateur WorldEdit/FAWE. */
    @FunctionalInterface
    public interface SelectionImporter {
        Optional<ArenaBounds> selectionOf(PlayerId playerId);
    }
}
