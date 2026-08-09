package io.github.youpl.diglikesteve.integration.worldedit;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import io.github.youpl.diglikesteve.application.port.outbound.LogPort;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.platform.paper.command.SelectionDraft;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

/**
 * Import d'une sélection WorldEdit, compatible FAWE (DAT §3 et §13.2).
 *
 * <p>Cet adaptateur ne sert qu'à <em>importer</em> deux coins : il ne pilote jamais le moteur de
 * remplissage. La sûreté du jeu ne dépend donc d'aucun moteur tiers, et le mode natif par
 * coordonnées reste le chemin garanti — en particulier sur Folia, où la déclaration Bukkit
 * publique de FAWE n'annonce pas {@code folia-supported}.</p>
 */
public final class WorldEditSelectionAdapter implements SelectionDraft.SelectionImporter {

    private final WorldEditPlugin worldEdit;
    private final LogPort log;

    private WorldEditSelectionAdapter(WorldEditPlugin worldEdit, LogPort log) {
        this.worldEdit = Objects.requireNonNull(worldEdit, "worldEdit");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Instancie l'adaptateur si et seulement si un plugin de sélection est réellement chargé
     * par la plateforme.
     */
    public static Optional<WorldEditSelectionAdapter> detect(LogPort log) {
        for (String candidate : new String[] {"FastAsyncWorldEdit", "WorldEdit"}) {
            var plugin = Bukkit.getPluginManager().getPlugin(candidate);
            if (plugin == null || !plugin.isEnabled()) {
                continue;
            }
            if (!(plugin instanceof WorldEditPlugin worldEdit)) {
                continue;
            }
            log.info("Import de sélection disponible", "plugin", candidate);
            return Optional.of(new WorldEditSelectionAdapter(worldEdit, log));
        }
        return Optional.empty();
    }

    @Override
    public Optional<ArenaBounds> selectionOf(PlayerId playerId) {
        Player player = Bukkit.getPlayer(playerId.value());
        if (player == null) {
            return Optional.empty();
        }
        try {
            var session = worldEdit.getSession(player);
            var world = session.getSelectionWorld();
            if (world == null) {
                return Optional.empty();
            }
            Region region = session.getSelection(world);
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            // Une sélection multi-monde est impossible ici : la région appartient à un seul monde.
            return Optional.of(ArenaBounds.of(
                    world.getName(),
                    new BlockPos(min.x(), min.y(), min.z()),
                    new BlockPos(max.x(), max.y(), max.z())));
        } catch (Exception incomplete) {
            log.debug("Sélection WorldEdit indisponible", "player", playerId,
                    "raison", incomplete.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
