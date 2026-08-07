package io.github.skyblueheat.echoclaims.domain.review;

/**
 * Comparison state of an evidence item between pre-event and post-event snapshots.
 *
 * <p>These are comparison states, not final provenance conclusions. EchoClaims
 * must not claim destroyed, despawned, transferred, or picked-up unless current
 * evidence directly supports those states.</p>
 */
public enum EvidenceComparisonState {
    PRESENT_BEFORE_ONLY,
    PRESENT_AFTER_ONLY,
    PRESENT_IN_BOTH,
    QUANTITY_DECREASED,
    QUANTITY_INCREASED,
    CHANGED,
    UNKNOWN
}
