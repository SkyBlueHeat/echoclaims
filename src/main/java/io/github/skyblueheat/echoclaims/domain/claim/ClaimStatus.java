package io.github.skyblueheat.echoclaims.domain.claim;

/**
 * Lifecycle state of a claim.
 *
 * <p>Every claim starts as {@code DRAFT}. A player submits it to move it to
 * {@code SUBMITTED}. A player may cancel their own claim to move it to
 * {@code CANCELLED}. Approval, rejection, and refund states are reserved for
 * future sprints and are intentionally absent from this enum.</p>
 */
public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    CANCELLED
}
