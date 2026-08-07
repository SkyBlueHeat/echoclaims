package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision;
import io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqliteClaimReviewItemDecisionRepository implements ClaimReviewItemDecisionRepository {

    private final DatabaseManager databaseManager;

    public SqliteClaimReviewItemDecisionRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String COLUMNS =
            "id, review_id, claim_id, evidence_item_reference, source_snapshot_id, "
                    + "original_quantity, approved_quantity, outcome, reason_code, "
                    + "staff_note, created_at, updated_at, version, metadata";

    @Override
    public void insert(ClaimReviewItemDecision decision) throws SQLException {
        Objects.requireNonNull(decision, "decision");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
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

    @Override
    public boolean update(ClaimReviewItemDecision decision, int expectedVersion) throws SQLException {
        Objects.requireNonNull(decision, "decision");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
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

    @Override
    public Optional<ClaimReviewItemDecision> findByReviewIdAndEvidenceRef(
            UUID reviewId, String evidenceItemReference) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(evidenceItemReference, "evidenceItemReference");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_review_item_decisions "
                             + "WHERE review_id = ? AND evidence_item_reference = ?")) {
            statement.setString(1, reviewId.toString());
            statement.setString(2, evidenceItemReference);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readDecision(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<ClaimReviewItemDecision> findByReviewId(UUID reviewId) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_review_item_decisions "
                             + "WHERE review_id = ? ORDER BY created_at ASC")) {
            statement.setString(1, reviewId.toString());
            return readDecisionList(statement);
        }
    }

    @Override
    public List<ClaimReviewItemDecision> findByClaimId(UUID claimId) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_review_item_decisions "
                             + "WHERE claim_id = ? ORDER BY created_at ASC")) {
            statement.setString(1, claimId.toString());
            return readDecisionList(statement);
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claim_review_item_decisions");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static List<ClaimReviewItemDecision> readDecisionList(PreparedStatement statement) throws SQLException {
        List<ClaimReviewItemDecision> decisions = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                decisions.add(readDecision(rs));
            }
        }
        return List.copyOf(decisions);
    }

    private static ClaimReviewItemDecision readDecision(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID reviewId = UUID.fromString(rs.getString("review_id"));
        UUID claimId = UUID.fromString(rs.getString("claim_id"));
        String evidenceRef = rs.getString("evidence_item_reference");
        UUID sourceSnapshotId = UUID.fromString(rs.getString("source_snapshot_id"));
        int originalQty = rs.getInt("original_quantity");
        int approvedQty = rs.getInt("approved_quantity");
        ReviewItemOutcome outcome = ReviewItemOutcome.valueOf(rs.getString("outcome"));
        ReviewReasonCode reasonCode = ReviewReasonCode.valueOf(rs.getString("reason_code"));
        String staffNote = rs.getString("staff_note");
        long createdAt = rs.getLong("created_at");
        long updatedAt = rs.getLong("updated_at");
        int version = rs.getInt("version");
        String metadataEncoded = rs.getString("metadata");

        return new ClaimReviewItemDecision(
                id, reviewId, claimId, evidenceRef, sourceSnapshotId,
                originalQty, approvedQty, outcome, reasonCode, staffNote,
                createdAt, updatedAt, version,
                MapCodec.decodeStringMap(metadataEncoded)
        );
    }
}
