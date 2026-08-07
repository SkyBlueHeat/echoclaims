package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment;
import io.github.skyblueheat.echoclaims.domain.review.CommentType;
import io.github.skyblueheat.echoclaims.domain.review.CommentVisibility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SqliteClaimReviewCommentRepository implements ClaimReviewCommentRepository {

    private final DatabaseManager databaseManager;

    public SqliteClaimReviewCommentRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String COLUMNS =
            "id, review_id, claim_id, actor_uuid, actor_type, visibility, "
                    + "comment_type, body, created_at, metadata";

    @Override
    public void insert(ClaimReviewComment comment) throws SQLException {
        Objects.requireNonNull(comment, "comment");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
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

    @Override
    public List<ClaimReviewComment> findByReviewId(UUID reviewId) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_review_comments "
                             + "WHERE review_id = ? ORDER BY created_at ASC")) {
            statement.setString(1, reviewId.toString());
            return readCommentList(statement);
        }
    }

    @Override
    public List<ClaimReviewComment> findByClaimId(UUID claimId) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_review_comments "
                             + "WHERE claim_id = ? ORDER BY created_at ASC")) {
            statement.setString(1, claimId.toString());
            return readCommentList(statement);
        }
    }

    @Override
    public List<ClaimReviewComment> findByClaimIdAndVisibility(UUID claimId, CommentVisibility visibility)
            throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(visibility, "visibility");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM claim_review_comments "
                             + "WHERE claim_id = ? AND visibility = ? ORDER BY created_at ASC")) {
            statement.setString(1, claimId.toString());
            statement.setString(2, visibility.name());
            return readCommentList(statement);
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claim_review_comments");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static List<ClaimReviewComment> readCommentList(PreparedStatement statement) throws SQLException {
        List<ClaimReviewComment> comments = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                comments.add(readComment(rs));
            }
        }
        return List.copyOf(comments);
    }

    private static ClaimReviewComment readComment(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID reviewId = UUID.fromString(rs.getString("review_id"));
        UUID claimId = UUID.fromString(rs.getString("claim_id"));
        UUID actorUuid = UUID.fromString(rs.getString("actor_uuid"));
        ClaimActorType actorType = ClaimActorType.valueOf(rs.getString("actor_type"));
        CommentVisibility visibility = CommentVisibility.valueOf(rs.getString("visibility"));
        CommentType commentType = CommentType.valueOf(rs.getString("comment_type"));
        String body = rs.getString("body");
        long createdAt = rs.getLong("created_at");
        String metadataEncoded = rs.getString("metadata");

        return new ClaimReviewComment(
                id, reviewId, claimId, actorUuid, actorType,
                visibility, commentType, body, createdAt,
                MapCodec.decodeStringMap(metadataEncoded)
        );
    }
}
