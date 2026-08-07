package io.github.skyblueheat.echoclaims.domain.review;

/**
 * Internal state of a {@link ClaimReview}.
 *
 * <p>A review starts as {@code OPEN} and becomes {@code FINALIZED} when the
 * assigned reviewer records a final decision. The claim status remains the
 * externally visible workflow state; this enum tracks only the review's
 * internal lifecycle.</p>
 */
public enum ReviewState {
    OPEN,
    FINALIZED
}
