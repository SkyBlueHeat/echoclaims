package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Atomic persistence of review operations that span multiple tables.
 *
 * <p>Every method executes all SQL statements inside a single transaction.
 * If any statement fails, the entire transaction is rolled back, guaranteeing
 * no orphan rows remain.</p>
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads,
 * never from the server main thread.</p>
 */
public interface ClaimReviewStore {

    /**
     * Atomically starts a review: inserts the review row, transitions the claim
     * to UNDER_REVIEW, and inserts the audit entry.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean startReview(
            ClaimReview review,
            UUID claimId,
            int expectedClaimVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Atomically takes over a review: updates the reviewer, and inserts the audit entry.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean takeoverReview(
            UUID reviewId,
            UUID newReviewerUuid,
            int expectedReviewVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Atomically adds an internal note: inserts the comment and audit entry.
     */
    void addInternalNote(
            ClaimReviewComment comment,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Atomically requests information: inserts the comment, transitions the claim
     * to WAITING_FOR_PLAYER, and inserts the audit entry.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean requestInformation(
            ClaimReviewComment comment,
            UUID claimId,
            int expectedClaimVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Atomically records a player response: inserts the comment, transitions the
     * claim back to UNDER_REVIEW, and inserts the audit entry.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean recordPlayerResponse(
            ClaimReviewComment comment,
            UUID claimId,
            int expectedClaimVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Atomically creates an item decision: inserts the decision and audit entry.
     */
    void createItemDecision(
            ClaimReviewItemDecision decision,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Atomically updates an item decision: updates the decision and inserts the audit entry.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean updateItemDecision(
            ClaimReviewItemDecision decision,
            int expectedDecisionVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Atomically finalizes a review: updates the review to FINALIZED with the
     * outcome, transitions the claim to the final status, inserts a final
     * summary comment, and inserts the audit entry.
     *
     * @return true if successful, false if optimistic version mismatch
     */
    boolean finalizeReview(
            UUID reviewId,
            ReviewOutcome finalOutcome,
            String finalSummary,
            long finalizedAt,
            int expectedReviewVersion,
            UUID claimId,
            ClaimStatus finalClaimStatus,
            int expectedClaimVersion,
            ClaimReviewComment finalSummaryComment,
            ClaimAuditEntry auditEntry
    ) throws SQLException;
}
