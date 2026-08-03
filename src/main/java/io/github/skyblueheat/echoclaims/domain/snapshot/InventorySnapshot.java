package io.github.skyblueheat.echoclaims.domain.snapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable inventory snapshot capturing a player's full state at a point in time.
 *
 * <p>Instances are created on the server thread by converting live Bukkit objects into
 * immutable records. Once constructed, a snapshot retains no reference to any mutable
 * Bukkit object and may safely cross into asynchronous persistence.</p>
 *
 * <p>Slot identifiers are unique across the entire snapshot (inventory, armor, offhand,
 * and cursor). This invariant is enforced in the constructor.</p>
 */
public record InventorySnapshot(
        java.util.UUID id,
        java.util.UUID playerUuid,
        CaptureReason captureReason,
        long capturedAt,
        String worldId,
        Coordinates coordinates,
        String gameMode,
        double health,
        int foodLevel,
        int experienceLevel,
        int totalExperience,
        List<SnapshotItem> inventoryItems,
        List<SnapshotItem> armorItems,
        SnapshotItem offhandItem,
        SnapshotItem cursorItem,
        int schemaVersion
) {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    public InventorySnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(captureReason, "captureReason");
        if (capturedAt < 0) {
            throw new IllegalArgumentException("capturedAt cannot be negative");
        }
        worldId = Objects.requireNonNull(worldId, "worldId").strip();
        if (worldId.isEmpty()) {
            throw new IllegalArgumentException("worldId cannot be blank");
        }
        gameMode = Objects.requireNonNullElse(gameMode, "").strip().toLowerCase(java.util.Locale.ROOT);
        health = Math.max(0, health);
        foodLevel = Math.max(0, Math.min(20, foodLevel));
        experienceLevel = Math.max(0, experienceLevel);
        totalExperience = Math.max(0, totalExperience);
        inventoryItems = List.copyOf(Objects.requireNonNullElse(inventoryItems, List.of()));
        armorItems = List.copyOf(Objects.requireNonNullElse(armorItems, List.of()));
        schemaVersion = Math.max(1, schemaVersion);

        enforceSlotUniqueness(inventoryItems, armorItems, offhandItem, cursorItem);
    }

    public static int currentSchemaVersion() {
        return CURRENT_SCHEMA_VERSION;
    }

    public boolean hasCoordinates() {
        return coordinates != null;
    }

    public boolean hasOffhandItem() {
        return offhandItem != null && !offhandItem.isAir();
    }

    public boolean hasCursorItem() {
        return cursorItem != null && !cursorItem.isAir();
    }

    public List<SnapshotItem> allItems() {
        List<SnapshotItem> all = new ArrayList<>(inventoryItems.size() + armorItems.size() + 2);
        all.addAll(inventoryItems);
        all.addAll(armorItems);
        if (offhandItem != null) {
            all.add(offhandItem);
        }
        if (cursorItem != null) {
            all.add(cursorItem);
        }
        return List.copyOf(all);
    }

    public int itemCount() {
        int count = inventoryItems.size() + armorItems.size();
        if (offhandItem != null) {
            count++;
        }
        if (cursorItem != null) {
            count++;
        }
        return count;
    }

    private static void enforceSlotUniqueness(
            List<SnapshotItem> inventory,
            List<SnapshotItem> armor,
            SnapshotItem offhand,
            SnapshotItem cursor
    ) {
        Set<String> seen = new HashSet<>();
        for (SnapshotItem item : inventory) {
            if (!seen.add(item.slot())) {
                throw new IllegalArgumentException("Duplicate slot in inventory: " + item.slot());
            }
        }
        for (SnapshotItem item : armor) {
            if (!seen.add(item.slot())) {
                throw new IllegalArgumentException("Duplicate slot in armor: " + item.slot());
            }
        }
        if (offhand != null && !seen.add(offhand.slot())) {
            throw new IllegalArgumentException("Duplicate slot for offhand: " + offhand.slot());
        }
        if (cursor != null && !seen.add(cursor.slot())) {
            throw new IllegalArgumentException("Duplicate slot for cursor: " + cursor.slot());
        }
    }
}
