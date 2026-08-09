package io.github.youpl.diglikesteve.configuration.model;

import io.github.youpl.diglikesteve.application.service.MineWriteBudget;
import io.github.youpl.diglikesteve.core.arena.ArenaBounds;
import io.github.youpl.diglikesteve.core.arena.BlockPalette;
import io.github.youpl.diglikesteve.core.arena.SpawnSlotSettings;
import io.github.youpl.diglikesteve.core.game.GameSettings;
import io.github.youpl.diglikesteve.core.shared.WorldLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration complète et validée (DAT §17). L'objet est immuable et remplacé atomiquement :
 * un rechargement ne modifie jamais une configuration déjà publiée.
 *
 * @param arena             cuboïde configuré, absent tant que l'administrateur ne l'a pas défini
 * @param hazardMaterials   matériaux interdits près d'un spawn, TNT comprise
 * @param exitDestination   destination de sortie native
 */
public record DlsConfig(
        GameSettings game,
        Optional<ArenaBounds> arena,
        BlockPalette palette,
        Set<String> hazardMaterials,
        SpawnSlotSettings spawnSlots,
        MineWriteBudget budget,
        TntSettings tnt,
        RewardSettings reward,
        StorageSettings storage,
        IntegrationSettings integrations,
        Optional<WorldLocation> exitDestination,
        Optional<WorldLocation> warpAccessPoint) {

    public DlsConfig {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(palette, "palette");
        hazardMaterials = Set.copyOf(Objects.requireNonNull(hazardMaterials, "hazardMaterials"));
        Objects.requireNonNull(spawnSlots, "spawnSlots");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(tnt, "tnt");
        Objects.requireNonNull(reward, "reward");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(exitDestination, "exitDestination");
        Objects.requireNonNull(warpAccessPoint, "warpAccessPoint");
    }

    /**
     * Tableau {@code hazardous[paletteId]} exploité par le planner de spawn.
     */
    public boolean[] hazardousPaletteIds() {
        boolean[] hazardous = new boolean[palette.size()];
        for (String material : hazardMaterials) {
            int id = palette.idOf(material);
            if (id > 0) {
                hazardous[id] = true;
            }
        }
        return hazardous;
    }

    /** Une partie ne peut démarrer que si l'arène est définie. */
    public boolean isPlayable() {
        return arena.isPresent();
    }

    public DlsConfig withArena(ArenaBounds bounds) {
        return new DlsConfig(game, Optional.of(bounds), palette, hazardMaterials, spawnSlots, budget,
                tnt, reward, storage, integrations, exitDestination, warpAccessPoint);
    }

    public DlsConfig withGame(GameSettings updated) {
        return new DlsConfig(updated, arena, palette, hazardMaterials, spawnSlots, budget,
                tnt, reward, storage, integrations, exitDestination, warpAccessPoint);
    }
}
