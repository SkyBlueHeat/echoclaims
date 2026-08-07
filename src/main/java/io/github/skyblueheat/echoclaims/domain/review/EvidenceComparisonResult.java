package io.github.skyblueheat.echoclaims.domain.review;

import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;

import java.util.Objects;

/**
 * Immutable result of comparing a single evidence item between pre-event and
 * post-event snapshots.
 *
 * <p>The {@code evidenceItemReference} is a stable key from
 * {@link EvidenceItemKey}. The {@code state} is a comparison state, not a
 * provenance conclusion.</p>
 */
public record EvidenceComparisonResult(
        String evidenceItemReference,
        SnapshotItem beforeItem,
        SnapshotItem afterItem,
        EvidenceComparisonState state
) {

    public EvidenceComparisonResult {
        Objects.requireNonNull(evidenceItemReference, "evidenceItemReference");
        beforeItem = beforeItem;
        afterItem = afterItem;
        state = Objects.requireNonNull(state, "state");
    }

    public boolean hasBeforeItem() {
        return beforeItem != null && !beforeItem.isAir();
    }

    public boolean hasAfterItem() {
        return afterItem != null && !afterItem.isAir();
    }
}
