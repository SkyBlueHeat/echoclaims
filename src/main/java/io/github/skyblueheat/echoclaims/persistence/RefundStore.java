package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItemStatus;
import io.github.skyblueheat.echoclaims.domain.refund.RefundStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Atomic persistence of refund operations that span multiple tables.
 *
 * <p>Every method executes all SQL statements inside a single transaction.
 * If any statement fails, the entire transaction is rolled back, guaranteeing
 * no orphan rows remain.</p>
 *
 * <p>The {@code createRefund} method enforces database-level idempotency
 * through a UNIQUE constraint on {@code review_id}: creating a refund for
 * the same finalized review twice is rejected by the constraint.</p>
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads,
 * never from the server main thread.</p>
 */
public interface RefundStore {

    /**
     * Atomically creates a refund: inserts the refund row, all refund item rows,
     * the refund audit entry, and the claim audit entry. If any insert fails,
     * the entire transaction is rolled back.
     *
     * @throws SQLException if a database error occurs, including UNIQUE
     *         constraint violation if a refund already exists for the review
     */
    void createRefund(
            Refund refund,
            List<RefundItem> items,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException;

    /**
     * Atomically transitions the refund to DELIVERING and records audit entries.
     * Uses optimistic concurrency on the refund version.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean startDelivery(
            UUID refundId,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException;

    /**
     * Atomically updates a refund item's delivery progress and records audit.
     * Uses optimistic concurrency on the item version.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean updateItemDelivery(
            UUID itemId,
            int deliveredQuantity,
            RefundItemStatus newStatus,
            String failureReason,
            long updatedAt,
            int expectedItemVersion,
            RefundAuditEntry refundAudit
    ) throws SQLException;

    /**
     * Atomically completes a refund: sets status to COMPLETED with completedAt,
     * and records the completion audit entry. Uses optimistic concurrency.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean completeRefund(
            UUID refundId,
            long completedAt,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException;

    /**
     * Atomically marks a refund as FAILED and records audit entries.
     * Uses optimistic concurrency.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean failRefund(
            UUID refundId,
            String failureReason,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException;

    /**
     * Atomically retries a failed refund: transitions to READY or PENDING
     * and records audit. Uses optimistic concurrency.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean retryRefund(
            UUID refundId,
            RefundStatus targetStatus,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException;
}
