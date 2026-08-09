package io.github.youpl.diglikesteve.core.arena;

import io.github.youpl.diglikesteve.core.shared.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Réservation reproductible des poches de spawn (DAT §6.4).
 *
 * <p>Téléporter un joueur dans un cuboïde plein le ferait suffoquer : le planner creuse donc
 * une poche d'air, garantit un sol stable et purge les blocs dangereux du rayon de sécurité.</p>
 *
 * <p>La purge n'utilise jamais une suppression : elle <em>échange</em> le type dangereux avec un
 * type inoffensif tiré ailleurs dans la mine, hors de toute zone de sécurité. Les quotas exacts
 * calculés par {@link MinePlanFactory} restent donc valides après planification, et un bloc
 * dangereux ne peut pas être réintroduit près d'une autre poche.</p>
 */
public final class SpawnSlotPlanner {

    private SpawnSlotPlanner() {
    }

    /**
     * @param plan      plan modifié sur place (poches vidées, blocs dangereux échangés)
     * @param consumed  bitset où les positions non scorables sont pré-consommées
     * @param hazardous {@code hazardous[paletteId]} vrai pour les types interdits près d'un spawn
     * @param settings  contraintes de placement
     * @param seed      graine de session, pour une planification reproductible
     */
    public static List<SpawnSlot> plan(
            MinePlan plan,
            ConsumedIndex consumed,
            boolean[] hazardous,
            SpawnSlotSettings settings,
            long seed) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(consumed, "consumed");
        Objects.requireNonNull(hazardous, "hazardous");
        Objects.requireNonNull(settings, "settings");

        ArenaBounds bounds = plan.bounds();
        int half = settings.halfWidth();

        int spanX = bounds.sizeX() - 2 * half;
        int spanZ = bounds.sizeZ() - 2 * half;
        // Un bloc de sol sous les pieds, puis la hauteur libre de la poche.
        int spanY = bounds.sizeY() - settings.pocketHeight() - 1;
        if (spanX <= 0 || spanZ <= 0 || spanY <= 0) {
            throw new IllegalStateException("Arène trop petite pour une poche de spawn "
                    + settings.pocketWidth() + "x" + settings.pocketWidth() + "x" + settings.pocketHeight());
        }

        SplittableRandom random = new SplittableRandom(seed ^ 0x53_50_41_57_4EL);
        List<SpawnSlot> slots = new ArrayList<>(settings.count());
        long minDistanceSquared = (long) settings.minDistance() * settings.minDistance();

        int attempts = 0;
        while (slots.size() < settings.count() && attempts < settings.maxAttempts()) {
            attempts++;
            int x = bounds.minX() + half + random.nextInt(spanX);
            int z = bounds.minZ() + half + random.nextInt(spanZ);
            int y = bounds.minY() + 1 + random.nextInt(spanY);
            BlockPos feet = new BlockPos(x, y, z);

            if (tooClose(slots, feet, minDistanceSquared)) {
                continue;
            }
            slots.add(carve(plan, consumed, settings, random, feet));
        }

        if (slots.size() < settings.count()) {
            throw new IllegalStateException("Seulement " + slots.size() + " poches de spawn placées sur "
                    + settings.count() + " demandées : réduisez le nombre de joueurs ou agrandissez l'arène");
        }

        // Purge en seconde passe : toutes les poches sont connues, aucun échange ne peut
        // ramener un bloc dangereux dans la zone de sécurité d'un autre slot.
        purgeHazards(plan, hazardous, settings, slots, random);
        return List.copyOf(slots);
    }

    private static boolean tooClose(List<SpawnSlot> slots, BlockPos candidate, long minDistanceSquared) {
        for (SpawnSlot slot : slots) {
            if (slot.feet().distanceSquared(candidate) < minDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private static SpawnSlot carve(
            MinePlan plan,
            ConsumedIndex consumed,
            SpawnSlotSettings settings,
            SplittableRandom random,
            BlockPos feet) {
        ArenaBounds bounds = plan.bounds();
        int half = settings.halfWidth();
        int pocketSize = settings.pocketWidth() * settings.pocketWidth() * settings.pocketHeight();
        List<BlockPos> cleared = new ArrayList<>(pocketSize);

        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 0; dy < settings.pocketHeight(); dy++) {
                    BlockPos pos = new BlockPos(feet.x() + dx, feet.y() + dy, feet.z() + dz);
                    int index = bounds.index(pos);
                    if (index < 0) {
                        continue;
                    }
                    plan.clearToAir(index);
                    // Une position vidée ne doit jamais rapporter de points.
                    consumed.markUnscorable(index);
                    cleared.add(pos);
                }
            }
        }
        return new SpawnSlot(feet, randomYaw(random), cleared);
    }

    /**
     * Échange chaque bloc dangereux d'une zone de sécurité contre un bloc inoffensif situé
     * hors de toutes les zones de sécurité. Le sol de la poche est couvert par ce balayage :
     * il ne peut donc jamais rester en TNT.
     */
    private static void purgeHazards(
            MinePlan plan,
            boolean[] hazardous,
            SpawnSlotSettings settings,
            List<SpawnSlot> slots,
            SplittableRandom random) {
        ArenaBounds bounds = plan.bounds();
        int radius = settings.hazardSafeRadius();
        int half = settings.halfWidth();

        for (SpawnSlot slot : slots) {
            BlockPos feet = slot.feet();
            for (int x = feet.x() - half - radius; x <= feet.x() + half + radius; x++) {
                for (int z = feet.z() - half - radius; z <= feet.z() + half + radius; z++) {
                    for (int y = feet.y() - 1 - radius; y <= feet.y() + settings.pocketHeight() + radius; y++) {
                        int index = bounds.index(x, y, z);
                        if (index < 0 || !isHazard(hazardous, plan.typeAt(index))) {
                            continue;
                        }
                        swapWithSafeBlock(plan, hazardous, settings, slots, random, index);
                    }
                }
            }
        }
    }

    private static boolean isHazard(boolean[] hazardous, byte type) {
        int id = Byte.toUnsignedInt(type);
        return id < hazardous.length && hazardous[id];
    }

    private static void swapWithSafeBlock(
            MinePlan plan,
            boolean[] hazardous,
            SpawnSlotSettings settings,
            List<SpawnSlot> slots,
            SplittableRandom random,
            int hazardIndex) {
        int volume = plan.volume();
        for (int attempt = 0; attempt < 128; attempt++) {
            int candidate = random.nextInt(volume);
            if (isSwappable(plan, hazardous, settings, slots, candidate)) {
                plan.swap(hazardIndex, candidate);
                return;
            }
        }
        // Aucune cible trouvée par tirage : balayage linéaire déterministe.
        for (int candidate = 0; candidate < volume; candidate++) {
            if (isSwappable(plan, hazardous, settings, slots, candidate)) {
                plan.swap(hazardIndex, candidate);
                return;
            }
        }
        // Mine intégralement dangereuse : on neutralise plutôt que de laisser une TNT au spawn.
        plan.clearToAir(hazardIndex);
    }

    private static boolean isSwappable(
            MinePlan plan,
            boolean[] hazardous,
            SpawnSlotSettings settings,
            List<SpawnSlot> slots,
            int index) {
        byte type = plan.typeAt(index);
        if (type == BlockPalette.AIR_ID || isHazard(hazardous, type)) {
            return false;
        }
        return !isInAnySafetyZone(plan.bounds().positionOf(index), settings, slots);
    }

    private static boolean isInAnySafetyZone(BlockPos pos, SpawnSlotSettings settings, List<SpawnSlot> slots) {
        int reach = settings.halfWidth() + settings.hazardSafeRadius();
        for (SpawnSlot slot : slots) {
            BlockPos feet = slot.feet();
            boolean insideX = Math.abs(pos.x() - feet.x()) <= reach;
            boolean insideZ = Math.abs(pos.z() - feet.z()) <= reach;
            boolean insideY = pos.y() >= feet.y() - 1 - settings.hazardSafeRadius()
                    && pos.y() <= feet.y() + settings.pocketHeight() + settings.hazardSafeRadius();
            if (insideX && insideZ && insideY) {
                return true;
            }
        }
        return false;
    }

    private static float randomYaw(SplittableRandom random) {
        return random.nextInt(4) * 90.0f;
    }
}
