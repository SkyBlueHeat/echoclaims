package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite implementation of {@link ClaimReviewStore}.
 *
 * <p>Every method executes all SQL statements inside a single transaction.
 * If any statement fails, the entire transaction is rolled back, guaranteeing
 * no orphan rows remain.</p>
 */
public final class SqliteClaimReviewStore implements ClaimReviewStore {

    private final DatabaseManager databaseManager;

    public SqliteClaimReviewStore(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public boolean startReview(
            ClaimReview review,
            UUID claimId,
            int expectedClaimVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertReviewRow(connection, review);
                boolean claimUpdated = updateClaimStatus(
                        connection, claimId, ClaimStatus.UNDER_REVIEW,
                        0, 0, expectedClaimVersion);
                if (!claimUpdated) {
                    connection.rollback();
                    return false;
                }
                insertAuditRow(connection, auditEntry);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public boolean takeoverReview(
            UUID reviewId,
            UUID newReviewerUuid,
            int expectedReviewVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(newReviewerUuid, "newReviewerUuid");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean reviewUpdated = updateReviewReviewer(
                        connection, reviewId, newReviewerUuid, expectedReviewVersion);
                if (!reviewUpdated) {
                    connection.rollback();
                    return false;
                }
                insertAuditRow(connection, auditEntry);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void addInternalNote(ClaimReviewComment comment, ClaimAuditEntry auditEntry) throws SQLException {
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertCommentRow(connection, comment);
                insertAuditRow(connection, auditEntry);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public boolean requestInformation(
            ClaimReviewComment comment,
            UUID claimId,
            int expectedClaimVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException {
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertCommentRow(connection, comment);
                boolean claimUpdated = updateClaimStatus(
                        connection, claimId, ClaimStatus.WAITING_FOR_PLAYER,
                        0, 0, expectedClaimVersion);
                if (!claimUpdated) {
                    connection.rollback();
                    return false;
                }
                insertAuditRow(connection, auditEntry);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public boolean recordPlayerResponse(
            ClaimReviewComment comment,
            UUID claimId,
            int expectedClaimVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException {
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertCommentRow(connection, comment);
                boolean claimUpdated = updateClaimStatus(
                        connection, claimId, ClaimStatus.UNDER_REVIEW,
                        0, 0, expectedClaimVersion);
                if (!claimUpdated) {
                    connection.rollback();
                    return false;
                }
                insertAuditRow(connection, auditEntry);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void createItemDecision(
            ClaimReviewItemDecision decision,
            ClaimAuditEntry auditEntry
    ) throws SQLException {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertDecisionRow(connection, decision);
                insertAuditRow(connection, auditEntry);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public boolean updateItemDecision(
            ClaimReviewItemDecision decision,
            int expectedDecisionVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean decisionUpdated = updateDecisionRow(
                        connection, decision, expectedDecisionVersion);
                if (!decisionUpdated) {
                    connection.rollback();
                    return false;
                }
                insertAuditRow(connection, auditEntry);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public boolean finalizeReview(
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
    ) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(finalOutcome, "finalOutcome");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(finalClaimStatus, "finalClaimStatus");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean reviewUpdated = updateReviewFinalized(
                        connection, reviewId, finalOutcome, finalSummary,
                        finalizedAt, expectedReviewVersion);
                if (!reviewUpdated) {
                    connection.rollback();
                    return false;
                }
                boolean claimUpdated = updateClaimStatus(
                        connection, claimId, finalClaimStatus,
                        0, 0, expectedClaimVersion);
                if (!claimUpdated) {
                    connection.rollback();
                    return false;
                }
                if (finalSummaryComment != null) {
                    insertCommentRow(connection, finalSummaryComment);
                }
                insertAuditRow(connection, auditEntry);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static void insertReviewRow(Connection connection, ClaimReview review) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO claim_reviews (id, claim_id, assigned_reviewer_uuid, "
                        + "review_state, final_outcome, final_summary, started_at, "
                        + "updated_at, finalized_at, version, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, review.id().toString());
            statement.setString(2, review.claimId().toString());
            statement.setString(3, review.assignedReviewerUuid().toString());
            statement.setString(4, review.reviewState().name());
            if (review.finalOutcome() != null) {
                statement.setString(5, review.finalOutcome().name());
            } else {
                statement.setNull(5, Types.VARCHAR);
            }
            statement.setString(6, review.finalSummary());
            statement.setLong(7, review.startedAt());
            statement.setLong(8, review.updatedAt());
            statement.setLong(9, review.finalizedAt());
            statement.setInt(10, review.version());
            statement.setString(11, MapCodec.encodeStringMap(review.metadata()));
            statement.executeUpdate();
        }
    }

    private static void insertCommentRow(Connection connection, ClaimReviewComment comment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO claim_review_comments (id, review_id, claim_id, "
                        + "actor_uuid, actor_type, visibility, comment_type, "
                        + "body, created_at, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, comment.id().toString());
            statement.setString(2, comment.reviewId().toString());
            statement.setString(3, comment.claimId().toString());
            statement.setString(4, comment.actorUuid().toString());
            statement.setString(5, comment.actorType().name());
            statement.setString(6, comment.visibility().name());
            statement.setString(7, comment.commentType().name());
            statement.setString(8, comment.body());
            statement.setLong(9, comment.createdAt());
            statement.setString(10, MapCodec.encodeStringMap(comment.metadata()));
            statement.executeUpdate();
        }
    }

    private static void insertDecisionRow(Connection connection, ClaimReviewItemDecision decision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO claim_review_item_decisions (id, review_id, claim_id, "
                        + "evidence_item_reference, source_snapshot_id, original_quantity, "
                        + "approved_quantity, outcome, reason_code, staff_note, "
                        + "created_at, updated_at, version, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, decision.id().toString());
            statement.setString(2, decision.reviewId().toString());
            statement.setString(3, decision.claimId().toString());
            statement.setString(4, decision.evidenceItemReference());
            statement.setString(5, decision.sourceSnapshotId().toString());
            statement.setInt(6, decision.originalQuantity());
            statement.setInt(7, decision.approvedQuantity());
            statement.setString(8, decision.outcome().name());
            statement.setString(9, decision.reasonCode().name());
            statement.setString(10, decision.staffNote());
            statement.setLong(11, decision.createdAt());
            statement.setLong(12, decision.updatedAt());
            statement.setInt(13, decision.version());
            statement.setString(14, MapCodec.encodeStringMap(decision.metadata()));
            statement.executeUpdate();
        }
    }

    private static void insertAuditRow(Connection connection, ClaimAuditEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO claim_audit_entries (id, claim_id, actor_type, actor_uuid, "
                        + "action, reason, recorded_at, claim_version, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.claimId().toString());
            statement.setString(3, entry.actorType().name());
            if (entry.actorUuid() != null) {
                statement.setString(4, entry.actorUuid().toString());
            } else {
                statement.setNull(4, Types.VARCHAR);
            }
            statement.setString(5, entry.action().name());
            statement.setString(6, entry.reason());
            statement.setLong(7, entry.recordedAt());
            statement.setInt(8, entry.claimVersion());
            statement.setString(9, MapCodec.encodeStringMap(entry.metadata()));
            statement.executeUpdate();
        }
    }

    private static boolean updateClaimStatus(
            Connection connection, UUID claimId, ClaimStatus newStatus,
            long submittedAt, long cancelledAt, int expectedVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE claims SET status = ?, submitted_at = ?, cancelled_at = ?, "
                        + "version = version + 1 WHERE id = ? AND version = ?")) {
            statement.setString(1, newStatus.name());
            statement.setLong(2, submittedAt);
            statement.setLong(3, cancelledAt);
            statement.setString(4, claimId.toString());
            statement.setInt(5, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    private static boolean updateReviewReviewer(
            Connection connection, UUID reviewId, UUID newReviewerUuid, int expectedVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE claim_reviews SET assigned_reviewer_uuid = ?, "
                        + "updated_at = ?, version = version + 1 "
                        + "WHERE id = ? AND version = ?")) {
            statement.setString(1, newReviewerUuid.toString());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, reviewId.toString());
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    private static boolean updateReviewFinalized(
            Connection connection, UUID reviewId, ReviewOutcome outcome,
            String summary, long finalizedAt, int expectedVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE claim_reviews SET review_state = 'FINALIZED', "
                        + "final_outcome = ?, final_summary = ?, "
                        + "finalized_at = ?, updated_at = ?, "
                        + "version = version + 1 "
                        + "WHERE id = ? AND version = ?")) {
            statement.setString(1, outcome.name());
            statement.setString(2, summary);
            statement.setLong(3, finalizedAt);
            statement.setLong(4, finalizedAt);
            statement.setString(5, reviewId.toString());
            statement.setInt(6, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    private static boolean updateDecisionRow(
            Connection connection, ClaimReviewItemDecision decision, int expectedVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE claim_review_item_decisions SET "
                        + "approved_quantity = ?, outcome = ?, reason_code = ?, "
                        + "staff_note = ?, updated_at = ?, version = version + 1 "
                        + "WHERE id = ? AND version = ?")) {
            statement.setInt(1, decision.approvedQuantity());
            statement.setString(2, decision.outcome().name());
            statement.setString(3, decision.reasonCode().name());
            statement.setString(4, decision.staffNote());
            statement.setLong(5, System.currentTimeMillis());
            statement.setString(6, decision.id().toString());
            statement.setInt(7, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }
}
