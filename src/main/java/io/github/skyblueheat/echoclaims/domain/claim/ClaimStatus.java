package io.github.skyblueheat.echoclaims.domain.claim;

/**
 * Lifecycle state of a claim.
 *
 * <p>Every claim starts as {@code DRAFT}. A player submits it to move it to
 * {@code SUBMITTED}. Staff start a review to move it to {@code UNDER_REVIEW}.
 * Staff may request information to move it to {@code WAITING_FOR_PLAYER}.
 * A player responds to return it to {@code UNDER_REVIEW}. Staff finalize
 * the review as {@code APPROVED}, {@code PARTIALLY_APPROVED}, or
 * {@code REJECTED}. A player may cancel their own claim to move it to
 * {@code CANCELLED} only while it is {@code DRAFT} or {@code SUBMITTED}.</p>
 *
 * <p>Terminal states are {@code APPROVED}, {@code PARTIALLY_APPROVED},
 * {@code REJECTED}, and {@code CANCELLED}. No transition out of a terminal
 * state is allowed.</p>
 */
public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    WAITING_FOR_PLAYER,
    APPROVED,
    PARTIALLY_APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == APPROVED || this == PARTIALLY_APPROVED
                || this == REJECTED || this == CANCELLED;
    }

    public boolean isUnderReview() {
        return this == UNDER_REVIEW || this == WAITING_FOR_PLAYER;
    }
}
