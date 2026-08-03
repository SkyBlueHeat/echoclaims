package io.github.skyblueheat.echoclaims.domain.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySnapshotTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID SNAPSHOT = UUID.randomUUID();

    private SnapshotItem item(String slot) {
        return new SnapshotItem(slot, "minecraft:stone", 1, "", "", 5, "", 0, 0, Map.of());
    }

    @Test
    void emptySnapshotIsValid() {
        assertDoesNotThrow(() -> new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(1, 2, 3), "survival",
                20.0, 20, 10, 100,
                List.of(), List.of(), null, null, 1
        ));
    }

    @Test
    void allItemsAggregatesInventoryArmorOffhandCursor() {
        SnapshotItem inv = item("0");
        SnapshotItem armor = item("36");
        SnapshotItem offhand = item("40");
        SnapshotItem cursor = item("cursor");

        InventorySnapshot snapshot = new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20.0, 20, 0, 0,
                List.of(inv), List.of(armor), offhand, cursor, 1
        );

        assertEquals(4, snapshot.allItems().size());
        assertEquals(4, snapshot.itemCount());
    }

    @Test
    void duplicateSlotAcrossInventoryAndArmorThrows() {
        SnapshotItem inv = item("0");
        SnapshotItem armor = item("0");

        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20.0, 20, 0, 0,
                List.of(inv), List.of(armor), null, null, 1
        ));
    }

    @Test
    void duplicateSlotWithOffhandThrows() {
        SnapshotItem inv = item("40");
        SnapshotItem offhand = item("40");

        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20.0, 20, 0, 0,
                List.of(inv), List.of(), offhand, null, 1
        ));
    }

    @Test
    void blankWorldIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "  ", new Coordinates(0, 0, 0), "survival",
                20.0, 20, 0, 0,
                List.of(), List.of(), null, null, 1
        ));
    }

    @Test
    void negativeCapturedAtThrows() {
        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, -1L,
                "world", new Coordinates(0, 0, 0), "survival",
                20.0, 20, 0, 0,
                List.of(), List.of(), null, null, 1
        ));
    }

    @Test
    void nullPlayerUuidThrows() {
        assertThrows(NullPointerException.class, () -> new InventorySnapshot(
                SNAPSHOT, null, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20.0, 20, 0, 0,
                List.of(), List.of(), null, null, 1
        ));
    }

    @Test
    void gameModeIsNormalizedToLowerCase() {
        InventorySnapshot snapshot = new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "SURVIVAL",
                20.0, 20, 0, 0,
                List.of(), List.of(), null, null, 1
        );

        assertEquals("survival", snapshot.gameMode());
    }

    @Test
    void foodLevelIsClampedTo20() {
        InventorySnapshot snapshot = new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20.0, 99, 0, 0,
                List.of(), List.of(), null, null, 1
        );

        assertEquals(20, snapshot.foodLevel());
    }

    @Test
    void hasCoordinatesReturnsTrueWhenPresent() {
        InventorySnapshot with = new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(1, 2, 3), "survival",
                20.0, 20, 0, 0,
                List.of(), List.of(), null, null, 1
        );
        assertTrue(with.hasCoordinates());

        InventorySnapshot without = new InventorySnapshot(
                SNAPSHOT, PLAYER, CaptureReason.PRE_DEATH, 1000L,
                "world", null, "survival",
                20.0, 20, 0, 0,
                List.of(), List.of(), null, null, 1
        );
        assertFalse(without.hasCoordinates());
    }

    @Test
    void currentSchemaVersionIsPositive() {
        assertTrue(InventorySnapshot.currentSchemaVersion() >= 1);
    }
}
