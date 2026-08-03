package dev.worldecho.domain.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure-Java, testable component that compares two {@link ObservedInventorySnapshot}s
 * and produces a diff describing what changed.
 *
 * <p>Encapsulates the slot snapshot comparison logic that was previously implicit
 * in the reconciler's processing loop.  This component is unit-testable without Bukkit.
 *
 * <p>Comparison is by slot position ({@code inventorySection} + {@code slotIndex}).
 * Changes in material, amount, existing identity, or classification are detected.
 */
public final class SlotSnapshotComparator {

    public enum ChangeType {
        ADDED,
        REMOVED,
        MATERIAL_CHANGED,
        AMOUNT_CHANGED,
        IDENTITY_CHANGED,
        CLASSIFICATION_CHANGED,
        UNCHANGED
    }

    public record SlotDiff(
            String inventorySection,
            int slotIndex,
            ChangeType changeType,
            ObservedInventorySlot previous,
            ObservedInventorySlot current
    ) {
        public SlotDiff {
            inventorySection = inventorySection == null ? "" : inventorySection.strip();
            Objects.requireNonNull(changeType, "changeType");
        }
    }

    public record SnapshotDiff(
            List<SlotDiff> diffs,
            int addedCount,
            int removedCount,
            int changedCount,
            int unchangedCount
    ) {
        public SnapshotDiff {
            diffs = diffs == null ? List.of() : List.copyOf(diffs);
        }

        public boolean hasChanges() {
            return addedCount > 0 || removedCount > 0 || changedCount > 0;
        }

        public List<SlotDiff> changedSlots() {
            return diffs.stream()
                    .filter(d -> d.changeType() != ChangeType.UNCHANGED)
                    .toList();
        }
    }

    /**
     * Compares two snapshots and returns a diff.
     *
     * @param previous the previous snapshot (may be null for first observation)
     * @param current  the current snapshot
     * @return the diff
     */
    public SnapshotDiff compare(ObservedInventorySnapshot previous, ObservedInventorySnapshot current) {
        Objects.requireNonNull(current, "current");

        List<SlotDiff> diffs = new ArrayList<>();
        int added = 0, removed = 0, changed = 0, unchanged = 0;

        // Build a map of previous slots by section:index
        java.util.Map<String, ObservedInventorySlot> prevMap = new java.util.HashMap<>();
        if (previous != null) {
            for (ObservedInventorySlot slot : previous.slots()) {
                prevMap.put(slotKey(slot), slot);
            }
        }

        java.util.Set<String> seenKeys = new java.util.HashSet<>();

        for (ObservedInventorySlot currentSlot : current.slots()) {
            String key = slotKey(currentSlot);
            seenKeys.add(key);

            ObservedInventorySlot prevSlot = prevMap.get(key);
            if (prevSlot == null) {
                diffs.add(new SlotDiff(currentSlot.inventorySection(), currentSlot.slotIndex(),
                        ChangeType.ADDED, null, currentSlot));
                added++;
            } else {
                ChangeType changeType = classifyChange(prevSlot, currentSlot);
                diffs.add(new SlotDiff(currentSlot.inventorySection(), currentSlot.slotIndex(),
                        changeType, prevSlot, currentSlot));
                if (changeType == ChangeType.UNCHANGED) {
                    unchanged++;
                } else {
                    changed++;
                }
            }
        }

        // Find removed slots
        if (previous != null) {
            for (ObservedInventorySlot prevSlot : previous.slots()) {
                String key = slotKey(prevSlot);
                if (!seenKeys.contains(key)) {
                    diffs.add(new SlotDiff(prevSlot.inventorySection(), prevSlot.slotIndex(),
                            ChangeType.REMOVED, prevSlot, null));
                    removed++;
                }
            }
        }

        return new SnapshotDiff(diffs, added, removed, changed, unchanged);
    }

    private static String slotKey(ObservedInventorySlot slot) {
        return slot.inventorySection() + ":" + slot.slotIndex();
    }

    private static ChangeType classifyChange(ObservedInventorySlot prev, ObservedInventorySlot curr) {
        // Both empty = unchanged
        if (prev.isEmpty() && curr.isEmpty()) {
            return ChangeType.UNCHANGED;
        }

        // Material changed
        if (!prev.material().equals(curr.material())) {
            return ChangeType.MATERIAL_CHANGED;
        }

        // Amount changed
        if (prev.amount() != curr.amount()) {
            return ChangeType.AMOUNT_CHANGED;
        }

        // Identity changed (UNIQUE ID)
        if (!java.util.Objects.equals(prev.existingUniqueId(), curr.existingUniqueId())) {
            return ChangeType.IDENTITY_CHANGED;
        }

        // Classification changed
        if (prev.classification() != null && curr.classification() != null) {
            if (prev.classification().mode() != curr.classification().mode()) {
                return ChangeType.CLASSIFICATION_CHANGED;
            }
        }

        return ChangeType.UNCHANGED;
    }
}
