package io.github.skyblueheat.echoclaims.domain.refund;

/**
 * The kind of action recorded in a refund audit entry.
 *
 * <p>Each value corresponds to a lifecycle event in the refund state machine.
 * Together with {@link RefundAuditEntry} they form the complete, append-only
 * audit trail for every refund operation.</p>
 */
public enum RefundAction {
    REFUND_CREATED,
    REFUND_EXECUTION_STARTED,
    REFUND_ITEM_DELIVERED,
    REFUND_PARTIALLY_DELIVERED,
    REFUND_COMPLETED,
    REFUND_FAILED,
    REFUND_RETRIED
}
