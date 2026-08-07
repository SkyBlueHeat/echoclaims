package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqliteClaimReviewRepository implements ClaimReviewRepository {

    private final DatabaseManager databaseManager;

    public SqliteClaimReviewRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String COLUMNS =
            "id, claim_id, assigned_reviewer_uuid, review_state, final_outcome, "
                    + "final_summary, started_at, updated_at, finalized_at, version, metadata";

    @Override
    public void insert(ClaimReview review) throws SQLException {
        Objects.requireNonNull(review, "review");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
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

    @Override
    public Optional<ClaimReview> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_reviews WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readReview(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<ClaimReview> findByClaimId(UUID claimId) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_reviews WHERE claim_id = ?")) {
            statement.setString(1, claimId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readReview(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean updateReviewer(UUID reviewId, UUID newReviewerUuid, int expectedVersion) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(newReviewerUuid, "newReviewerUuid");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
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

    @Override
    public boolean finalizeReview(
            UUID reviewId,
            ReviewOutcome finalOutcome,
            String finalSummary,
            long finalizedAt,
            int expectedVersion
    ) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(finalOutcome, "finalOutcome");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE claim_reviews SET review_state = 'FINALIZED', "
                             + "final_outcome = ?, final_summary = ?, "
                             + "finalized_at = ?, updated_at = ?, "
                             + "version = version + 1 "
                             + "WHERE id = ? AND version = ?")) {
            statement.setString(1, finalOutcome.name());
            statement.setString(2, finalSummary);
            statement.setLong(3, finalizedAt);
            statement.setLong(4, finalizedAt);
            statement.setString(5, reviewId.toString());
            statement.setInt(6, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public List<ClaimReview> findByReviewer(UUID reviewerUuid, int limit) throws SQLException {
        Objects.requireNonNull(reviewerUuid, "reviewerUuid");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_reviews "
                             + "WHERE assigned_reviewer_uuid = ? "
                             + "ORDER BY started_at DESC LIMIT ?")) {
            statement.setString(1, reviewerUuid.toString());
            statement.setInt(2, safeLimit);
            return readReviewList(statement);
        }
    }

    @Override
    public long countByState(ReviewState state) throws SQLException {
        Objects.requireNonNull(state, "state");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claim_reviews WHERE review_state = ?")) {
            statement.setString(1, state.name());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claim_reviews");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static List<ClaimReview> readReviewList(PreparedStatement statement) throws SQLException {
        List<ClaimReview> reviews = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                reviews.add(readReview(rs));
            }
        }
        return List.copyOf(reviews);
    }

    private static ClaimReview readReview(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID claimId = UUID.fromString(rs.getString("claim_id"));
        UUID reviewerUuid = UUID.fromString(rs.getString("assigned_reviewer_uuid"));
        ReviewState state = ReviewState.valueOf(rs.getString("review_state"));
        String outcomeStr = rs.getString("final_outcome");
        ReviewOutcome outcome = outcomeStr != null ? ReviewOutcome.valueOf(outcomeStr) : null;
        String summary = rs.getString("final_summary");
        long startedAt = rs.getLong("started_at");
        long updatedAt = rs.getLong("updated_at");
        long finalizedAt = rs.getLong("finalized_at");
        int version = rs.getInt("version");
        String metadataEncoded = rs.getString("metadata");

        return new ClaimReview(
                id, claimId, reviewerUuid, state, outcome, summary,
                startedAt, updatedAt, finalizedAt, version,
                MapCodec.decodeStringMap(metadataEncoded)
        );
    }
}
