package io.github.skyblueheat.echoclaims.domain.claim;

/**
 * The kind of action recorded in a {@link ClaimAuditEntry}.
 *
 * <p>Each value corresponds to a lifecycle event in the claim state machine,
 * including refund and delivery actions.</p>
 */
public enum ClaimAction {
    CREATED,
    SUBMITTED,
    CANCELLED,
    REVIEW_STARTED,
    REVIEW_TAKEN_OVER,
    INTERNAL_NOTE_ADDED,
    INFORMATION_REQUESTED,
    PLAYER_RESPONDED,
    ITEM_DECISION_CREATED,
    ITEM_DECISION_UPDATED,
    REVIEW_APPROVED,
    REVIEW_PARTIALLY_APPROVED,
    REVIEW_REJECTED,
    REFUND_CREATED,
    REFUND_EXECUTION_STARTED,
    REFUND_ITEM_DELIVERED,
    REFUND_PARTIALLY_DELIVERED,
    REFUND_COMPLETED,
    REFUND_FAILED,
    REFUND_RETRIED
}
