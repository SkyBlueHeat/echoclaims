package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItemStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqliteRefundItemRepository implements RefundItemRepository {

    private final DatabaseManager databaseManager;

    public SqliteRefundItemRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String COLUMNS =
            "id, refund_id, claim_id, review_id, player_uuid, evidence_item_reference, "
                    + "source_snapshot_id, material_key, refundable_quantity, delivered_quantity, "
                    + "serialized_item_data, status, failure_reason, created_at, updated_at, version";

    @Override
    public void insert(RefundItem item) throws SQLException {
        Objects.requireNonNull(item, "item");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO refund_items (id, refund_id, claim_id, review_id, "
                             + "player_uuid, evidence_item_reference, source_snapshot_id, "
                             + "material_key, refundable_quantity, delivered_quantity, "
                             + "serialized_item_data, status, failure_reason, "
                             + "created_at, updated_at, version) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, item.id().toString());
            statement.setString(2, item.refundId().toString());
            statement.setString(3, item.claimId().toString());
            statement.setString(4, item.reviewId().toString());
            statement.setString(5, item.playerUuid().toString());
            statement.setString(6, item.evidenceItemReference());
            statement.setString(7, item.sourceSnapshotId().toString());
            statement.setString(8, item.materialKey());
            statement.setInt(9, item.refundableQuantity());
            statement.setInt(10, item.deliveredQuantity());
            statement.setString(11, item.serializedItemData());
            statement.setString(12, item.status().name());
            statement.setString(13, item.failureReason());
            statement.setLong(14, item.createdAt());
            statement.setLong(15, item.updatedAt());
            statement.setInt(16, item.version());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<RefundItem> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_items WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readItem(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<RefundItem> findByRefundId(UUID refundId) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_items "
                             + "WHERE refund_id = ? ORDER BY created_at ASC")) {
            statement.setString(1, refundId.toString());
            return readItemList(statement);
        }
    }

    @Override
    public List<RefundItem> findByClaimId(UUID claimId) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_items "
                             + "WHERE claim_id = ? ORDER BY created_at ASC")) {
            statement.setString(1, claimId.toString());
            return readItemList(statement);
        }
    }

    @Override
    public List<RefundItem> findByPlayerUuid(UUID playerUuid) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_items "
                             + "WHERE player_uuid = ? ORDER BY created_at DESC")) {
            statement.setString(1, playerUuid.toString());
            return readItemList(statement);
        }
    }

    @Override
    public List<RefundItem> findPendingByRefundId(UUID refundId) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_items "
                             + "WHERE refund_id = ? AND status IN ('PENDING', 'PARTIALLY_DELIVERED') "
                             + "ORDER BY created_at ASC")) {
            statement.setString(1, refundId.toString());
            return readItemList(statement);
        }
    }

    @Override
    public boolean updateDeliveryProgress(
            UUID itemId,
            int deliveredQuantity,
            RefundItemStatus newStatus,
            String failureReason,
            long updatedAt,
            int expectedVersion
    ) throws SQLException {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(newStatus, "newStatus");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE refund_items SET delivered_quantity = ?, status = ?, "
                             + "failure_reason = ?, updated_at = ?, version = version + 1 "
                             + "WHERE id = ? AND version = ?")) {
            statement.setInt(1, deliveredQuantity);
            statement.setString(2, newStatus.name());
            statement.setString(3, failureReason == null ? "" : failureReason);
            statement.setLong(4, updatedAt);
            statement.setString(5, itemId.toString());
            statement.setInt(6, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public long countByStatus(RefundItemStatus status) throws SQLException {
        Objects.requireNonNull(status, "status");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM refund_items WHERE status = ?")) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static List<RefundItem> readItemList(PreparedStatement statement) throws SQLException {
        List<RefundItem> items = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(readItem(rs));
            }
        }
        return List.copyOf(items);
    }

    private static RefundItem readItem(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID refundId = UUID.fromString(rs.getString("refund_id"));
        UUID claimId = UUID.fromString(rs.getString("claim_id"));
        UUID reviewId = UUID.fromString(rs.getString("review_id"));
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String evidenceRef = rs.getString("evidence_item_reference");
        UUID snapshotId = UUID.fromString(rs.getString("source_snapshot_id"));
        String materialKey = rs.getString("material_key");
        int refundableQty = rs.getInt("refundable_quantity");
        int deliveredQty = rs.getInt("delivered_quantity");
        String serializedData = rs.getString("serialized_item_data");
        RefundItemStatus status = RefundItemStatus.valueOf(rs.getString("status"));
        String failureReason = rs.getString("failure_reason");
        long createdAt = rs.getLong("created_at");
        long updatedAt = rs.getLong("updated_at");
        int version = rs.getInt("version");

        return new RefundItem(
                id, refundId, claimId, reviewId, playerUuid,
                evidenceRef, snapshotId, materialKey,
                refundableQty, deliveredQty, serializedData,
                status, failureReason, createdAt, updatedAt, version
        );
    }
}
