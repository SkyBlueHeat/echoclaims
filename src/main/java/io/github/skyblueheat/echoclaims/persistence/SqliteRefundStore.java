package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItemStatus;
import io.github.skyblueheat.echoclaims.domain.refund.RefundStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite implementation of {@link RefundStore}.
 *
 * <p>Every method executes all SQL statements inside a single transaction.
 * If any statement fails, the entire transaction is rolled back, guaranteeing
 * no orphan rows remain.</p>
 *
 * <p>The {@code createRefund} method relies on the UNIQUE constraint on
 * {@code refunds.review_id} for database-level idempotency. If a refund
 * already exists for the same review, the INSERT fails and the entire
 * transaction is rolled back.</p>
 */
public final class SqliteRefundStore implements RefundStore {

    private final DatabaseManager databaseManager;

    public SqliteRefundStore(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public void createRefund(
            Refund refund,
            List<RefundItem> items,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException {
        Objects.requireNonNull(refund, "refund");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(refundAudit, "refundAudit");
        Objects.requireNonNull(claimAudit, "claimAudit");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertRefundRow(connection, refund);
                for (RefundItem item : items) {
                    insertRefundItemRow(connection, item);
                }
                insertRefundAuditRow(connection, refundAudit);
                insertClaimAuditRow(connection, claimAudit);
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
    public boolean startDelivery(
            UUID refundId,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(refundAudit, "refundAudit");
        Objects.requireNonNull(claimAudit, "claimAudit");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean refundUpdated = updateRefundStatus(
                        connection, refundId, RefundStatus.DELIVERING, 0, expectedRefundVersion);
                if (!refundUpdated) {
                    connection.rollback();
                    return false;
                }
                insertRefundAuditRow(connection, refundAudit);
                insertClaimAuditRow(connection, claimAudit);
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
    public boolean updateItemDelivery(
            UUID itemId,
            int deliveredQuantity,
            RefundItemStatus newStatus,
            String failureReason,
            long updatedAt,
            int expectedItemVersion,
            RefundAuditEntry refundAudit
    ) throws SQLException {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(refundAudit, "refundAudit");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean itemUpdated = updateRefundItemRow(
                        connection, itemId, deliveredQuantity, newStatus,
                        failureReason, updatedAt, expectedItemVersion);
                if (!itemUpdated) {
                    connection.rollback();
                    return false;
                }
                insertRefundAuditRow(connection, refundAudit);
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
    public boolean completeRefund(
            UUID refundId,
            long completedAt,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(refundAudit, "refundAudit");
        Objects.requireNonNull(claimAudit, "claimAudit");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean refundUpdated = updateRefundStatus(
                        connection, refundId, RefundStatus.COMPLETED, completedAt, expectedRefundVersion);
                if (!refundUpdated) {
                    connection.rollback();
                    return false;
                }
                insertRefundAuditRow(connection, refundAudit);
                insertClaimAuditRow(connection, claimAudit);
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
    public boolean failRefund(
            UUID refundId,
            String failureReason,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(refundAudit, "refundAudit");
        Objects.requireNonNull(claimAudit, "claimAudit");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean refundUpdated = updateRefundStatus(
                        connection, refundId, RefundStatus.FAILED, 0, expectedRefundVersion);
                if (!refundUpdated) {
                    connection.rollback();
                    return false;
                }
                insertRefundAuditRow(connection, refundAudit);
                insertClaimAuditRow(connection, claimAudit);
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
    public boolean retryRefund(
            UUID refundId,
            RefundStatus targetStatus,
            int expectedRefundVersion,
            RefundAuditEntry refundAudit,
            ClaimAuditEntry claimAudit
    ) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(targetStatus, "targetStatus");
        Objects.requireNonNull(refundAudit, "refundAudit");
        Objects.requireNonNull(claimAudit, "claimAudit");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean refundUpdated = updateRefundStatus(
                        connection, refundId, targetStatus, 0, expectedRefundVersion);
                if (!refundUpdated) {
                    connection.rollback();
                    return false;
                }
                insertRefundAuditRow(connection, refundAudit);
                insertClaimAuditRow(connection, claimAudit);
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

    // ─── Private insert/update helpers ───

    private static void insertRefundRow(Connection connection, Refund refund) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
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

    private static void insertRefundItemRow(Connection connection, RefundItem item) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
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

    private static void insertRefundAuditRow(Connection connection, RefundAuditEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO refund_audit_entries (id, refund_id, claim_id, review_id, "
                        + "player_uuid, actor_uuid, action, refund_item_id, quantity, "
                        + "reason, recorded_at, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.refundId().toString());
            statement.setString(3, entry.claimId().toString());
            statement.setString(4, entry.reviewId().toString());
            statement.setString(5, entry.playerUuid().toString());
            if (entry.actorUuid() != null) {
                statement.setString(6, entry.actorUuid().toString());
            } else {
                statement.setNull(6, Types.VARCHAR);
            }
            statement.setString(7, entry.action().name());
            if (entry.refundItemId() != null) {
                statement.setString(8, entry.refundItemId().toString());
            } else {
                statement.setNull(8, Types.VARCHAR);
            }
            statement.setInt(9, entry.quantity());
            statement.setString(10, entry.reason());
            statement.setLong(11, entry.recordedAt());
            statement.setString(12, MapCodec.encodeStringMap(entry.metadata()));
            statement.executeUpdate();
        }
    }

    private static void insertClaimAuditRow(Connection connection, ClaimAuditEntry entry) throws SQLException {
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

    private static boolean updateRefundStatus(
            Connection connection, UUID refundId, RefundStatus newStatus,
            long completedAt, int expectedVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE refunds SET status = ?, completed_at = ?, "
                        + "version = version + 1 WHERE id = ? AND version = ? "
                        + "AND status != 'COMPLETED'")) {
            statement.setString(1, newStatus.name());
            statement.setLong(2, completedAt);
            statement.setString(3, refundId.toString());
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    private static boolean updateRefundItemRow(
            Connection connection, UUID itemId, int deliveredQuantity,
            RefundItemStatus newStatus, String failureReason,
            long updatedAt, int expectedVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
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
}
