package io.github.youpl.diglikesteve.core.arena;

import io.github.youpl.diglikesteve.core.shared.BlockPos;
import io.github.youpl.diglikesteve.core.shared.PlayerId;
import io.github.youpl.diglikesteve.testsupport.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Poches de spawn sûres et réservation atomique des slots (DAT §6.4). */
class SpawnSlotPlannerTest {

    @Test
    @DisplayName("les poches sont vidées, non scorables et exemptes de bloc dangereux")
    void pocketsAreSafeAndUnscorable() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(40);
        MinePlan plan = MinePlanFactory.generate(bounds, palette, 99L);
        ConsumedIndex consumed = new ConsumedIndex(plan.volume());
        boolean[] hazardous = Fixtures.tntHazard(palette);
        SpawnSlotSettings settings = new SpawnSlotSettings(8, 3, 2, 6, 2, 20_000);

        List<SpawnSlot> slots = SpawnSlotPlanner.plan(plan, consumed, hazardous, settings, 99L);
        assertEquals(8, slots.size());

        for (SpawnSlot slot : slots) {
            for (BlockPos cleared : slot.cleared()) {
                int index = bounds.index(cleared);
                assertEquals(BlockPalette.AIR_ID, plan.typeAt(index), "la poche doit être vidée");
                assertTrue(consumed.isConsumed(index), "une poche ne doit jamais rapporter de points");
            }
            assertNoHazardNear(plan, hazardous, settings, slot);
        }
    }

    @Test
    @DisplayName("les échanges de sécurité préservent exactement les quotas")
    void hazardSwapPreservesQuotas() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(40);
        MinePlan plan = MinePlanFactory.generate(bounds, palette, 4321L);

        int[] before = count(plan, palette.size());
        int clearedBefore = 0;

        ConsumedIndex consumed = new ConsumedIndex(plan.volume());
        SpawnSlotSettings settings = new SpawnSlotSettings(6, 3, 2, 8, 2, 20_000);
        List<SpawnSlot> slots = SpawnSlotPlanner.plan(plan, consumed, Fixtures.tntHazard(palette), settings, 4321L);
        for (SpawnSlot slot : slots) {
            clearedBefore += slot.cleared().size();
        }

        int[] after = count(plan, palette.size());
        int totalAfter = 0;
        for (int id = 1; id < palette.size(); id++) {
            totalAfter += after[id];
        }
        // Seules les positions vidées pour les poches disparaissent ; aucun autre type n'est perdu.
        int totalBefore = 0;
        for (int id = 1; id < palette.size(); id++) {
            totalBefore += before[id];
        }
        assertEquals(totalBefore - clearedBefore, totalAfter, "aucun bloc ne doit disparaître hors des poches");
    }

    @Test
    @DisplayName("une arène trop petite est refusée avec un diagnostic clair")
    void tooSmallArenaIsRejected() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(2);
        MinePlan plan = MinePlanFactory.generate(bounds, palette, 1L);
        ConsumedIndex consumed = new ConsumedIndex(plan.volume());

        assertThrows(IllegalStateException.class, () -> SpawnSlotPlanner.plan(plan, consumed,
                Fixtures.tntHazard(palette), SpawnSlotSettings.defaults(4), 1L));
    }

    @Test
    @DisplayName("un slot n'est jamais attribué deux fois")
    void slotReservationIsExclusive() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(40);
        MinePlan plan = MinePlanFactory.generate(bounds, palette, 5L);
        ConsumedIndex consumed = new ConsumedIndex(plan.volume());
        SpawnSlotSettings settings = new SpawnSlotSettings(3, 3, 2, 6, 2, 20_000);
        Arena arena = new Arena(plan, consumed,
                SpawnSlotPlanner.plan(plan, consumed, Fixtures.tntHazard(palette), settings, 5L));

        Set<BlockPos> taken = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            PlayerId playerId = PlayerId.of(UUID.randomUUID());
            BlockPos feet = arena.reserveSlot(playerId).orElseThrow().feet();
            assertTrue(taken.add(feet), "deux joueurs ne peuvent pas partager une poche");
        }
        // L'arène est pleine : l'inscription suivante doit être refusée proprement.
        assertTrue(arena.reserveSlot(PlayerId.of(UUID.randomUUID())).isEmpty());
    }

    @Test
    @DisplayName("réserver deux fois pour le même joueur rend le même slot")
    void reservationIsIdempotentPerPlayer() {
        BlockPalette palette = Fixtures.defaultPalette();
        ArenaBounds bounds = Fixtures.bounds(40);
        MinePlan plan = MinePlanFactory.generate(bounds, palette, 6L);
        ConsumedIndex consumed = new ConsumedIndex(plan.volume());
        SpawnSlotSettings settings = new SpawnSlotSettings(4, 3, 2, 6, 2, 20_000);
        Arena arena = new Arena(plan, consumed,
                SpawnSlotPlanner.plan(plan, consumed, Fixtures.tntHazard(palette), settings, 6L));

        PlayerId playerId = PlayerId.of(UUID.randomUUID());
        BlockPos first = arena.reserveSlot(playerId).orElseThrow().feet();
        BlockPos second = arena.reserveSlot(playerId).orElseThrow().feet();
        assertEquals(first, second);
        assertEquals(1, arena.reservedSlots());

        arena.releaseSlot(playerId);
        assertEquals(0, arena.reservedSlots());
        assertNotEquals(-1, bounds.index(first));
    }

    private static void assertNoHazardNear(MinePlan plan, boolean[] hazardous, SpawnSlotSettings settings,
                                           SpawnSlot slot) {
        ArenaBounds bounds = plan.bounds();
        int reach = settings.halfWidth() + settings.hazardSafeRadius();
        BlockPos feet = slot.feet();
        for (int x = feet.x() - reach; x <= feet.x() + reach; x++) {
            for (int z = feet.z() - reach; z <= feet.z() + reach; z++) {
                for (int y = feet.y() - 1 - settings.hazardSafeRadius();
                     y <= feet.y() + settings.pocketHeight() + settings.hazardSafeRadius(); y++) {
                    int index = bounds.index(x, y, z);
                    if (index < 0) {
                        continue;
                    }
                    int id = Byte.toUnsignedInt(plan.typeAt(index));
                    assertFalse(id < hazardous.length && hazardous[id],
                            "bloc dangereux à proximité du spawn " + feet);
                }
            }
        }
    }

    private static int[] count(MinePlan plan, int paletteSize) {
        int[] counts = new int[paletteSize];
        for (int index = 0; index < plan.volume(); index++) {
            counts[Byte.toUnsignedInt(plan.typeAt(index))]++;
        }
        return counts;
    }
}
