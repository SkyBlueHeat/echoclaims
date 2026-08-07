package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqliteRefundRepository implements RefundRepository {

    private final DatabaseManager databaseManager;

    public SqliteRefundRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String COLUMNS =
            "id, claim_id, review_id, player_uuid, status, created_at, "
                    + "completed_at, version, metadata";

    @Override
    public void insert(Refund refund) throws SQLException {
        Objects.requireNonNull(refund, "refund");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO refunds (id, claim_id, review_id, player_uuid, "
                             + "status, created_at, completed_at, version, metadata) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, refund.id().toString());
            statement.setString(2, refund.claimId().toString());
            statement.setString(3, refund.reviewId().toString());
            statement.setString(4, refund.playerUuid().toString());
            statement.setString(5, refund.status().name());
            statement.setLong(6, refund.createdAt());
            statement.setLong(7, refund.completedAt());
            statement.setInt(8, refund.version());
            statement.setString(9, MapCodec.encodeStringMap(refund.metadata()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Refund> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refunds WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readRefund(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Refund> findByClaimId(UUID claimId) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refunds WHERE claim_id = ?")) {
            statement.setString(1, claimId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readRefund(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Refund> findByReviewId(UUID reviewId) throws SQLException {
        Objects.requireNonNull(reviewId, "reviewId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refunds WHERE review_id = ?")) {
            statement.setString(1, reviewId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readRefund(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean updateStatus(UUID refundId, RefundStatus newStatus, long completedAt, int expectedVersion) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(newStatus, "newStatus");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE refunds SET status = ?, completed_at = ?, "
                             + "version = version + 1 WHERE id = ? AND version = ?")) {
            statement.setString(1, newStatus.name());
            statement.setLong(2, completedAt);
            statement.setString(3, refundId.toString());
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public List<Refund> findByPlayerUuid(UUID playerUuid, int limit) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refunds "
                             + "WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, safeLimit);
            return readRefundList(statement);
        }
    }

    @Override
    public List<Refund> findByPlayerUuidAndStatus(UUID playerUuid, RefundStatus status, int limit) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(status, "status");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refunds "
                             + "WHERE player_uuid = ? AND status = ? ORDER BY created_at DESC LIMIT ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, status.name());
            statement.setInt(3, safeLimit);
            return readRefundList(statement);
        }
    }

    @Override
    public List<Refund> findByStatus(RefundStatus status, int limit) throws SQLException {
        Objects.requireNonNull(status, "status");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refunds "
                             + "WHERE status = ? ORDER BY created_at DESC LIMIT ?")) {
            statement.setString(1, status.name());
            statement.setInt(2, safeLimit);
            return readRefundList(statement);
        }
    }

    @Override
    public long countByStatus(RefundStatus status) throws SQLException {
        Objects.requireNonNull(status, "status");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM refunds WHERE status = ?")) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM refunds");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static List<Refund> readRefundList(PreparedStatement statement) throws SQLException {
        List<Refund> refunds = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                refunds.add(readRefund(rs));
            }
        }
        return List.copyOf(refunds);
    }

    private static Refund readRefund(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID claimId = UUID.fromString(rs.getString("claim_id"));
        UUID reviewId = UUID.fromString(rs.getString("review_id"));
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        RefundStatus status = RefundStatus.valueOf(rs.getString("status"));
        long createdAt = rs.getLong("created_at");
        long completedAt = rs.getLong("completed_at");
        int version = rs.getInt("version");
        String metadataEncoded = rs.getString("metadata");

        return new Refund(
                id, claimId, reviewId, playerUuid, status,
                createdAt, completedAt, version,
                MapCodec.decodeStringMap(metadataEncoded)
        );
    }
}
