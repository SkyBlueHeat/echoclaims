package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SlotSnapshotComparatorTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final String SESSION_ID = "test-session";

    private final SlotSnapshotComparator comparator = new SlotSnapshotComparator();

    private ReconciliationCycle cycle(long seq) {
        return ReconciliationCycle.create(PLAYER, "test", seq, SESSION_ID, 0L);
    }

    private ObservedInventorySlot slot(String section, int index, String material, int amount,
                                       TrackedItemId uniqueId, IdentityMode mode) {
        IdentityClassificationResult classification = mode != null
                ? new IdentityClassificationResult(mode, List.of(), 1.0)
                : null;
        return new ObservedInventorySlot(
                section, index, material, amount,
                new dev.worldecho.domain.content.ContentKey("minecraft", material.replace("minecraft:", "")),
                "minecraft",
                ItemDescriptor.of(material, amount),
                classification,
                uniqueId, null, null, false
        );
    }

    private ObservedInventorySnapshot snapshot(List<ObservedInventorySlot> slots, long seq) {
        return new ObservedInventorySnapshot(PLAYER, "TestPlayer", slots, cycle(seq));
    }

    @Test
    void firstObservationTreatsAllSlotsAsAdded() {
        ObservedInventorySnapshot current = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE),
                slot("main", 1, "minecraft:cobblestone", 32, null, IdentityMode.LOT)
        ), 1L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(null, current);
        assertEquals(2, diff.addedCount());
        assertEquals(0, diff.removedCount());
        assertEquals(0, diff.changedCount());
        assertTrue(diff.hasChanges());
    }

    @Test
    void identicalSnapshotsProduceNoChanges() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(0, diff.addedCount());
        assertEquals(0, diff.removedCount());
        assertEquals(0, diff.changedCount());
        assertEquals(1, diff.unchangedCount());
        assertFalse(diff.hasChanges());
    }

    @Test
    void amountChangeIsDetected() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:cobblestone", 32, null, IdentityMode.LOT)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:cobblestone", 16, null, IdentityMode.LOT)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(1, diff.changedCount());
        assertEquals(SlotSnapshotComparator.ChangeType.AMOUNT_CHANGED, diff.diffs().get(0).changeType());
    }

    @Test
    void materialChangeIsDetected() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:cobblestone", 32, null, IdentityMode.LOT)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:dirt", 32, null, IdentityMode.LOT)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(SlotSnapshotComparator.ChangeType.MATERIAL_CHANGED, diff.diffs().get(0).changeType());
    }

    @Test
    void identityChangeIsDetected() {
        TrackedItemId id1 = TrackedItemId.random();
        TrackedItemId id2 = TrackedItemId.random();
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, id1, IdentityMode.UNIQUE)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, id2, IdentityMode.UNIQUE)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(SlotSnapshotComparator.ChangeType.IDENTITY_CHANGED, diff.diffs().get(0).changeType());
    }

    @Test
    void removedSlotIsDetected() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE),
                slot("main", 1, "minecraft:cobblestone", 32, null, IdentityMode.LOT)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(1, diff.removedCount());
        assertEquals(1, diff.unchangedCount());
    }

    @Test
    void addedSlotIsDetected() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE),
                slot("main", 1, "minecraft:cobblestone", 32, null, IdentityMode.LOT)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(1, diff.addedCount());
        assertEquals(1, diff.unchangedCount());
    }

    @Test
    void classificationChangeIsDetected() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:stick", 1, null, IdentityMode.UNIQUE)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:stick", 1, null, IdentityMode.LOT)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(SlotSnapshotComparator.ChangeType.CLASSIFICATION_CHANGED, diff.diffs().get(0).changeType());
    }

    @Test
    void changedSlotsFilterWorks() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE),
                slot("main", 1, "minecraft:cobblestone", 32, null, IdentityMode.LOT)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                slot("main", 0, "minecraft:diamond_sword", 1, null, IdentityMode.UNIQUE),
                slot("main", 1, "minecraft:cobblestone", 16, null, IdentityMode.LOT)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(1, diff.changedSlots().size());
        assertEquals(SlotSnapshotComparator.ChangeType.AMOUNT_CHANGED, diff.changedSlots().get(0).changeType());
    }

    @Test
    void bothEmptySlotsAreUnchanged() {
        ObservedInventorySnapshot prev = snapshot(List.of(
                new ObservedInventorySlot("main", 0, "", 0,
                        new dev.worldecho.domain.content.ContentKey("minecraft", "air"),
                        "minecraft", null, null, null, null, null, false)
        ), 1L);
        ObservedInventorySnapshot curr = snapshot(List.of(
                new ObservedInventorySlot("main", 0, "", 0,
                        new dev.worldecho.domain.content.ContentKey("minecraft", "air"),
                        "minecraft", null, null, null, null, null, false)
        ), 2L);

        SlotSnapshotComparator.SnapshotDiff diff = comparator.compare(prev, curr);
        assertEquals(1, diff.unchangedCount());
        assertEquals(0, diff.changedCount());
    }
}
