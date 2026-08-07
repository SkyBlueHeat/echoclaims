package io.github.skyblueheat.echoclaims.domain.review;

import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only service that compares pre-event and post-event snapshots.
 *
 * <p>Produces deterministic, bounded results with stable ordering. Never
 * fabricates item-loss conclusions. Never automatically approves or rejects
 * a claim. Never rewrites evidence. Missing post-event evidence is reported
 * as unavailable, not as a loss conclusion.</p>
 */
public final class EvidenceComparisonService {

    private EvidenceComparisonService() {
    }

    /**
     * Compares a pre-event snapshot against an optional post-event snapshot.
     *
     * <p>Results are ordered by slot identifier for deterministic output.
     * The list is bounded by the number of items in the pre-event snapshot
     * plus any items present only in the post-event snapshot.</p>
     *
     * @param preEventSnapshot the pre-event snapshot (required)
     * @param postEventSnapshot the post-event snapshot (may be null)
     * @return a list of comparison results, ordered by slot
     */
    public static List<EvidenceComparisonResult> compare(
            InventorySnapshot preEventSnapshot,
            InventorySnapshot postEventSnapshot
    ) {
        Objects.requireNonNull(preEventSnapshot, "preEventSnapshot");

        Map<String, SnapshotItem> beforeBySlot = new LinkedHashMap<>();
        for (SnapshotItem item : preEventSnapshot.allItems()) {
            if (!item.isAir()) {
                beforeBySlot.put(item.slot(), item);
            }
        }

        Map<String, SnapshotItem> afterBySlot = new LinkedHashMap<>();
        if (postEventSnapshot != null) {
            for (SnapshotItem item : postEventSnapshot.allItems()) {
                if (!item.isAir()) {
                    afterBySlot.put(item.slot(), item);
                }
            }
        }

        UUID preSnapshotUuid = preEventSnapshot.id();
        UUID postSnapshotUuid = postEventSnapshot != null ? postEventSnapshot.id() : null;

        List<EvidenceComparisonResult> results = new ArrayList<>();
        for (String slot : sortedKeys(beforeBySlot, afterBySlot)) {
            SnapshotItem before = beforeBySlot.get(slot);
            SnapshotItem after = afterBySlot.get(slot);

            String reference;
            if (before != null) {
                reference = EvidenceItemKey.of(preSnapshotUuid, slot);
            } else {
                reference = EvidenceItemKey.of(postSnapshotUuid, slot);
            }

            EvidenceComparisonState state = classify(before, after);
            results.add(new EvidenceComparisonResult(reference, before, after, state));
        }

        return List.copyOf(results);
    }

    private static EvidenceComparisonState classify(SnapshotItem before, SnapshotItem after) {
        boolean hasBefore = before != null && !before.isAir();
        boolean hasAfter = after != null && !after.isAir();

        if (hasBefore && !hasAfter) {
            return EvidenceComparisonState.PRESENT_BEFORE_ONLY;
        }
        if (!hasBefore && hasAfter) {
            return EvidenceComparisonState.PRESENT_AFTER_ONLY;
        }
        if (!hasBefore) {
            return EvidenceComparisonState.UNKNOWN;
        }

        if (!before.materialKey().equals(after.materialKey())) {
            return EvidenceComparisonState.CHANGED;
        }

        if (before.amount() > after.amount()) {
            return EvidenceComparisonState.QUANTITY_DECREASED;
        }
        if (before.amount() < after.amount()) {
            return EvidenceComparisonState.QUANTITY_INCREASED;
        }

        return EvidenceComparisonState.PRESENT_IN_BOTH;
    }

    private static List<String> sortedKeys(Map<String, ?>... maps) {
        List<String> keys = new ArrayList<>();
        for (Map<String, ?> map : maps) {
            for (String key : map.keySet()) {
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        keys.sort(String::compareTo);
        return keys;
    }
}
