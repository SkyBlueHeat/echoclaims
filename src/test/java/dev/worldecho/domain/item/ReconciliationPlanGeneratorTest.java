package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationPlanGeneratorTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final String SESSION_ID = "test-session";

    private final ReconciliationPlanGenerator generator = new ReconciliationPlanGenerator();

    private ReconciliationCycle cycle() {
        return ReconciliationCycle.create(PLAYER, "test", 1L, SESSION_ID, 0L);
    }

    private ObservedInventorySlot uniqueSlot(TrackedItemId existingId) {
        return new ObservedInventorySlot(
                "main", 0, "minecraft:diamond_sword", 1,
                new dev.worldecho.domain.content.ContentKey("minecraft", "diamond_sword"), "minecraft",
                ItemDescriptor.of("minecraft:diamond_sword", 1),
                new IdentityClassificationResult(IdentityMode.UNIQUE, List.of("damageable"), 1.0),
                existingId, null, null, false
        );
    }

    private ObservedInventorySlot lotSlot(int amount) {
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();
        return new ObservedInventorySlot(
                "main", 1, "minecraft:cobblestone", amount,
                new dev.worldecho.domain.content.ContentKey("minecraft", "cobblestone"), "minecraft",
                ItemDescriptor.of("minecraft:cobblestone", amount),
                new IdentityClassificationResult(IdentityMode.LOT, List.of("stackable"), 1.0),
                null, null, fp, false
        );
    }

    private ObservedInventorySlot emptySlot() {
        return new ObservedInventorySlot(
                "main", 2, "", 0,
                new dev.worldecho.domain.content.ContentKey("minecraft", "air"), "minecraft",
                ItemDescriptor.of("minecraft:air", 1),
                new IdentityClassificationResult(IdentityMode.UNIQUE, List.of(), 1.0),
                null, null, null, false
        );
    }

    private ObservedInventorySlot malformedSlot() {
        return new ObservedInventorySlot(
                "main", 3, "minecraft:iron_sword", 1,
                new dev.worldecho.domain.content.ContentKey("minecraft", "iron_sword"), "minecraft",
                ItemDescriptor.of("minecraft:iron_sword", 1),
                new IdentityClassificationResult(IdentityMode.UNIQUE, List.of("damageable"), 1.0),
                null, null, null, true
        );
    }

    private ObservedInventorySnapshot snapshot(List<ObservedInventorySlot> slots) {
        return new ObservedInventorySnapshot(PLAYER, "TestPlayer", slots, cycle());
    }

    @Test
    void emptySnapshotProducesEmptyPlan() {
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of()));
        assertTrue(plan.slotPlans().isEmpty());
        assertEquals(0, plan.actionableCount());
    }

    @Test
    void emptySlotIsSkipped() {
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(emptySlot())));
        assertEquals(1, plan.slotPlans().size());
        assertEquals(ReconciliationPlanGenerator.SlotAction.SKIP_EMPTY, plan.slotPlans().get(0).action());
        assertEquals(0, plan.actionableCount());
    }

    @Test
    void uniqueSlotWithoutExistingIdProcessesUnique() {
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(uniqueSlot(null))));
        assertEquals(ReconciliationPlanGenerator.SlotAction.PROCESS_UNIQUE, plan.slotPlans().get(0).action());
        assertEquals(1, plan.uniqueCount());
    }

    @Test
    void uniqueSlotWithExistingIdTriggersDuplicateCheck() {
        TrackedItemId id = TrackedItemId.random();
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(uniqueSlot(id))));
        assertEquals(ReconciliationPlanGenerator.SlotAction.PROCESS_UNIQUE_WITH_DUPLICATE_CHECK,
                plan.slotPlans().get(0).action());
        assertEquals(1, plan.uniqueCount());
    }

    @Test
    void lotSlotProcessesLot() {
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(lotSlot(32))));
        assertEquals(ReconciliationPlanGenerator.SlotAction.PROCESS_LOT, plan.slotPlans().get(0).action());
        assertEquals(1, plan.lotCount());
    }

    @Test
    void mixedSnapshotGeneratesCorrectPlan() {
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(
                        uniqueSlot(null),
                        lotSlot(32),
                        emptySlot(),
                        uniqueSlot(TrackedItemId.random())
                )));

        assertEquals(4, plan.slotPlans().size());
        assertEquals(2, plan.uniqueCount());
        assertEquals(1, plan.lotCount());
        assertEquals(1, plan.skipCount());
        assertEquals(3, plan.actionableCount());
    }

    @Test
    void malformedSlotIsProcessedAsUniqueWithDuplicateCheck() {
        // Malformed slots have existingUniqueId=null but malformedIdentity=true
        // The plan treats them as PROCESS_UNIQUE (the service handles the malformed check)
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(malformedSlot())));
        assertEquals(ReconciliationPlanGenerator.SlotAction.PROCESS_UNIQUE, plan.slotPlans().get(0).action());
    }

    @Test
    void joinReconciliationPlanHasCorrectCounts() {
        // Simulate a join: player has 2 unique items, 1 lot stack, 1 empty slot
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(
                        uniqueSlot(TrackedItemId.random()),
                        uniqueSlot(null),
                        lotSlot(64),
                        emptySlot()
                )));

        assertEquals(4, plan.slotPlans().size());
        assertEquals(2, plan.uniqueCount());
        assertEquals(1, plan.lotCount());
        assertEquals(1, plan.skipCount());
        assertEquals(3, plan.actionableCount());
        assertEquals(PLAYER, plan.playerUuid());
        assertEquals("TestPlayer", plan.playerDisplayName());
        assertNotNull(plan.cycle());
    }

    @Test
    void allEmptySlotsAreSkipped() {
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(
                        emptySlot(), emptySlot(), emptySlot()
                )));
        assertEquals(3, plan.skipCount());
        assertEquals(0, plan.actionableCount());
    }

    @Test
    void unclassifiedSlotIsSkipped() {
        ObservedInventorySlot unclassified = new ObservedInventorySlot(
                "main", 0, "minecraft:stick", 1,
                new dev.worldecho.domain.content.ContentKey("minecraft", "stick"), "minecraft",
                ItemDescriptor.of("minecraft:stick", 1),
                null, null, null, null, false
        );
        ReconciliationPlanGenerator.ReconciliationPlan plan =
                generator.generatePlan(snapshot(List.of(unclassified)));
        assertEquals(ReconciliationPlanGenerator.SlotAction.SKIP_UNCLASSIFIED, plan.slotPlans().get(0).action());
    }
}
