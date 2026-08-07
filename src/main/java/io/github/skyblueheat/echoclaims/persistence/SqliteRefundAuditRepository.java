package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.refund.RefundAction;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SqliteRefundAuditRepository implements RefundAuditRepository {

    private final DatabaseManager databaseManager;

    public SqliteRefundAuditRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String COLUMNS =
            "id, refund_id, claim_id, review_id, player_uuid, actor_uuid, "
                    + "action, refund_item_id, quantity, reason, recorded_at, metadata";

    @Override
    public void insert(RefundAuditEntry entry) throws SQLException {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
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

    @Override
    public List<RefundAuditEntry> findByRefundId(UUID refundId) throws SQLException {
        Objects.requireNonNull(refundId, "refundId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_audit_entries "
                             + "WHERE refund_id = ? ORDER BY recorded_at ASC")) {
            statement.setString(1, refundId.toString());
            return readAuditList(statement);
        }
    }

    @Override
    public List<RefundAuditEntry> findByClaimId(UUID claimId) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_audit_entries "
                             + "WHERE claim_id = ? ORDER BY recorded_at ASC")) {
            statement.setString(1, claimId.toString());
            return readAuditList(statement);
        }
    }

    @Override
    public List<RefundAuditEntry> findByPlayerUuid(UUID playerUuid, int limit) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM refund_audit_entries "
                             + "WHERE player_uuid = ? ORDER BY recorded_at DESC LIMIT ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, safeLimit);
            return readAuditList(statement);
        }
    }

    private static List<RefundAuditEntry> readAuditList(PreparedStatement statement) throws SQLException {
        List<RefundAuditEntry> entries = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                entries.add(readAudit(rs));
            }
        }
        return List.copyOf(entries);
    }

    private static RefundAuditEntry readAudit(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID refundId = UUID.fromString(rs.getString("refund_id"));
        UUID claimId = UUID.fromString(rs.getString("claim_id"));
        UUID reviewId = UUID.fromString(rs.getString("review_id"));
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String actorUuidStr = rs.getString("actor_uuid");
        UUID actorUuid = actorUuidStr != null ? UUID.fromString(actorUuidStr) : null;
        RefundAction action = RefundAction.valueOf(rs.getString("action"));
        String itemIdStr = rs.getString("refund_item_id");
        UUID itemId = itemIdStr != null ? UUID.fromString(itemIdStr) : null;
        int quantity = rs.getInt("quantity");
        String reason = rs.getString("reason");
        long recordedAt = rs.getLong("recorded_at");
        String metadataEncoded = rs.getString("metadata");

        return new RefundAuditEntry(
                id, refundId, claimId, reviewId, playerUuid,
                actorUuid, action, itemId, quantity, reason,
                recordedAt, MapCodec.decodeStringMap(metadataEncoded)
        );
    }
}
