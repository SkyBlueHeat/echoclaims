package dev.worldecho.persistence;

import dev.worldecho.domain.item.LotCompatibilityFingerprint;
import dev.worldecho.domain.item.LotLineageEntry;
import dev.worldecho.domain.item.LotRelationType;
import dev.worldecho.domain.item.OwnershipSubjectType;
import dev.worldecho.domain.item.TrackedItemLot;
import dev.worldecho.domain.item.TrackedItemLotId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteTrackedItemLotRepository implements TrackedItemLotRepository {

    private final DatabaseManager databaseManager;

    public SqliteTrackedItemLotRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public CreateResult create(TrackedItemLot lot) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO tracked_item_lots "
                             + "(lot_id, created_at, first_seen_at, last_seen_at, content_key, "
                             + "provider_id, material, fingerprint, initial_amount, current_amount, "
                             + "tracking_reason, created_by_subject, owner_type, owner_stable_id, owner_display_snapshot) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, lot.lotId().toString());
            statement.setLong(2, lot.createdAt().toEpochMilli());
            statement.setLong(3, lot.firstSeenAt().toEpochMilli());
            statement.setLong(4, lot.lastSeenAt().toEpochMilli());
            statement.setString(5, lot.contentKey().toString());
            statement.setString(6, lot.providerId());
            statement.setString(7, lot.material());
            statement.setString(8, lot.fingerprint().serialize());
            statement.setInt(9, lot.initialAmount());
            statement.setInt(10, lot.currentAmount());
            statement.setString(11, lot.trackingReason());
            statement.setString(12, lot.createdBySubject());
            statement.setString(13, lot.ownerType());
            statement.setString(14, lot.ownerStableId());
            statement.setString(15, lot.ownerDisplaySnapshot());
            int rows = statement.executeUpdate();
            return rows > 0 ? CreateResult.CREATED : CreateResult.ALREADY_EXISTS;
        }
    }

    @Override
    public ReconcileResult reconcileOwnerAggregates(
            OwnershipSubjectType ownerType,
            String ownerStableId,
            String ownerDisplaySnapshot,
            Map<LotCompatibilityFingerprint, Integer> observedAmountsByFingerprint,
            Instant observedAt
    ) {
        String typeToken = ownerType.token();
        String normalizedStableId = ownerStableId.toLowerCase(java.util.Locale.ROOT);
        long observedEpochMilli = observedAt.toEpochMilli();
        java.util.Set<String> observedFingerprints = new java.util.HashSet<>();

        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // 1. Upsert each observed fingerprint+amount
                for (Map.Entry<LotCompatibilityFingerprint, Integer> entry : observedAmountsByFingerprint.entrySet()) {
                    String fpSerialized = entry.getKey().serialize();
                    int totalAmount = Math.max(0, entry.getValue());
                    observedFingerprints.add(fpSerialized);

                    // Check if lot exists for this owner+fingerprint
                    try (PreparedStatement findStmt = connection.prepareStatement(
                            "SELECT lot_id, current_amount, owner_display_snapshot FROM tracked_item_lots "
                                    + "WHERE owner_type = ? AND owner_stable_id = ? AND fingerprint = ?")) {
                        findStmt.setString(1, typeToken);
                        findStmt.setString(2, normalizedStableId);
                        findStmt.setString(3, fpSerialized);
                        try (ResultSet rs = findStmt.executeQuery()) {
                            if (rs.next()) {
                                // Update existing lot
                                String lotId = rs.getString("lot_id");
                                try (PreparedStatement updateStmt = connection.prepareStatement(
                                        "UPDATE tracked_item_lots SET current_amount = ?, last_seen_at = ?, "
                                                + "owner_display_snapshot = ? WHERE lot_id = ?")) {
                                    updateStmt.setInt(1, totalAmount);
                                    updateStmt.setLong(2, observedEpochMilli);
                                    updateStmt.setString(3, ownerDisplaySnapshot == null ? "" : ownerDisplaySnapshot);
                                    updateStmt.setString(4, lotId);
                                    updateStmt.executeUpdate();
                                }
                            } else {
                                // Create new lot
                                String newLotId = java.util.UUID.randomUUID().toString();
                                try (PreparedStatement insertStmt = connection.prepareStatement(
                                        "INSERT OR IGNORE INTO tracked_item_lots "
                                                + "(lot_id, created_at, first_seen_at, last_seen_at, content_key, "
                                                + "provider_id, material, fingerprint, initial_amount, current_amount, "
                                                + "tracking_reason, created_by_subject, owner_type, owner_stable_id, owner_display_snapshot) "
                                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                                    insertStmt.setString(1, newLotId);
                                    insertStmt.setLong(2, observedEpochMilli);
                                    insertStmt.setLong(3, observedEpochMilli);
                                    insertStmt.setLong(4, observedEpochMilli);
                                    insertStmt.setString(5, entry.getKey().contentKeyId().isEmpty()
                                            ? typeToken + ":" + entry.getKey().material()
                                            : entry.getKey().contentKeyId());
                                    insertStmt.setString(6, entry.getKey().providerId());
                                    insertStmt.setString(7, entry.getKey().material());
                                    insertStmt.setString(8, fpSerialized);
                                    insertStmt.setInt(9, totalAmount);
                                    insertStmt.setInt(10, totalAmount);
                                    insertStmt.setString(11, "AUTOMATIC");
                                    insertStmt.setString(12, typeToken + ":" + normalizedStableId);
                                    insertStmt.setString(13, typeToken);
                                    insertStmt.setString(14, normalizedStableId);
                                    insertStmt.setString(15, ownerDisplaySnapshot == null ? "" : ownerDisplaySnapshot);
                                    insertStmt.executeUpdate();
                                }
                            }
                        }
                    }
                }

                // 2. Zero out existing lots for this owner whose fingerprint is absent from snapshot
                try (PreparedStatement findAbsentStmt = connection.prepareStatement(
                        "SELECT lot_id, fingerprint FROM tracked_item_lots "
                                + "WHERE owner_type = ? AND owner_stable_id = ? AND current_amount > 0")) {
                    findAbsentStmt.setString(1, typeToken);
                    findAbsentStmt.setString(2, normalizedStableId);
                    try (ResultSet rs = findAbsentStmt.executeQuery()) {
                        while (rs.next()) {
                            String lotId = rs.getString("lot_id");
                            String fp = rs.getString("fingerprint");
                            if (!observedFingerprints.contains(fp)) {
                                try (PreparedStatement zeroStmt = connection.prepareStatement(
                                        "UPDATE tracked_item_lots SET current_amount = 0, last_seen_at = ? WHERE lot_id = ?")) {
                                    zeroStmt.setLong(1, observedEpochMilli);
                                    zeroStmt.setString(2, lotId);
                                    zeroStmt.executeUpdate();
                                }
                            }
                        }
                    }
                }

                connection.commit();
                return ReconcileResult.SUCCESS;
            } catch (SQLException e) {
                connection.rollback();
                return ReconcileResult.FAILURE;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            return ReconcileResult.FAILURE;
        }
    }

    @Override
    public Optional<TrackedItemLot> findById(TrackedItemLotId lotId) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM tracked_item_lots WHERE lot_id = ?")) {
            statement.setString(1, lotId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<TrackedItemLot> findByFingerprint(LotCompatibilityFingerprint fingerprint) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM tracked_item_lots WHERE fingerprint = ? ORDER BY last_seen_at DESC LIMIT 1")) {
            statement.setString(1, fingerprint.serialize());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<TrackedItemLot> findByFingerprintAndOwner(
            LotCompatibilityFingerprint fingerprint, String ownerSubject) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM tracked_item_lots WHERE fingerprint = ? AND created_by_subject = ? "
                             + "ORDER BY last_seen_at DESC LIMIT 1")) {
            statement.setString(1, fingerprint.serialize());
            statement.setString(2, ownerSubject);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<TrackedItemLot> findByOwnerAndFingerprint(
            OwnershipSubjectType ownerType, String ownerStableId,
            LotCompatibilityFingerprint fingerprint) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM tracked_item_lots WHERE owner_type = ? AND owner_stable_id = ? AND fingerprint = ? "
                             + "ORDER BY last_seen_at DESC LIMIT 1")) {
            statement.setString(1, ownerType.token());
            statement.setString(2, ownerStableId);
            statement.setString(3, fingerprint.serialize());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<TrackedItemLot> findAllByOwner(
            OwnershipSubjectType ownerType, String ownerStableId) throws SQLException {
        List<TrackedItemLot> results = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM tracked_item_lots WHERE owner_type = ? AND owner_stable_id = ?")) {
            statement.setString(1, ownerType.token());
            statement.setString(2, ownerStableId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    @Override
    public void updateOwnerDisplaySnapshot(TrackedItemLotId lotId, String displayName) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE tracked_item_lots SET owner_display_snapshot = ? WHERE lot_id = ?")) {
            statement.setString(1, displayName == null ? "" : displayName);
            statement.setString(2, lotId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean exists(TrackedItemLotId lotId) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM tracked_item_lots WHERE lot_id = ?")) {
            statement.setString(1, lotId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void updateAmount(TrackedItemLotId lotId, int newAmount) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE tracked_item_lots SET current_amount = ? WHERE lot_id = ?")) {
            statement.setInt(1, Math.max(0, newAmount));
            statement.setString(2, lotId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void observe(TrackedItemLotId lotId, Instant lastSeenAt) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE tracked_item_lots SET last_seen_at = ? WHERE lot_id = ?")) {
            statement.setLong(1, lastSeenAt.toEpochMilli());
            statement.setString(2, lotId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public int count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tracked_item_lots");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public void appendLineage(LotLineageEntry entry) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO lot_lineage "
                             + "(entry_id, lot_id, related_lot_id, relation_type, "
                             + "lot_amount, related_amount, occurred_at, source, idempotency_key) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.entryId());
            statement.setString(2, entry.lotId().toString());
            statement.setString(3, entry.relatedLotId().toString());
            statement.setString(4, entry.relationType().token());
            statement.setInt(5, entry.lotAmount());
            statement.setInt(6, entry.relatedAmount());
            statement.setLong(7, entry.occurredAt().toEpochMilli());
            statement.setString(8, entry.source());
            statement.setString(9, entry.idempotencyKey());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<LotLineageEntry> findLineageByIdempotencyKey(TrackedItemLotId lotId, String idempotencyKey) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM lot_lineage WHERE lot_id = ? AND idempotency_key = ?")) {
            statement.setString(1, lotId.toString());
            statement.setString(2, idempotencyKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapLineageRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<LotLineageEntry> findLineage(TrackedItemLotId lotId) throws SQLException {
        List<LotLineageEntry> entries = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM lot_lineage WHERE lot_id = ? ORDER BY occurred_at DESC")) {
            statement.setString(1, lotId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapLineageRow(rs));
                }
            }
        }
        return entries;
    }

    private TrackedItemLot mapRow(ResultSet rs) throws SQLException {
        String ownerType = "";
        String ownerStableId = "";
        String ownerDisplaySnapshot = "";
        try {
            ownerType = rs.getString("owner_type");
            ownerStableId = rs.getString("owner_stable_id");
            ownerDisplaySnapshot = rs.getString("owner_display_snapshot");
        } catch (SQLException ignored) {
            // Columns may not exist before migration v4
        }
        return new TrackedItemLot(
                TrackedItemLotId.parse(rs.getString("lot_id")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("first_seen_at")),
                Instant.ofEpochMilli(rs.getLong("last_seen_at")),
                dev.worldecho.domain.content.ContentKey.parse(rs.getString("content_key")),
                rs.getString("provider_id"),
                rs.getString("material"),
                parseFingerprint(rs.getString("fingerprint")),
                rs.getInt("initial_amount"),
                rs.getInt("current_amount"),
                rs.getString("tracking_reason"),
                rs.getString("created_by_subject"),
                ownerType == null ? "" : ownerType,
                ownerStableId == null ? "" : ownerStableId,
                ownerDisplaySnapshot == null ? "" : ownerDisplaySnapshot
        );
    }

    private LotLineageEntry mapLineageRow(ResultSet rs) throws SQLException {
        return new LotLineageEntry(
                rs.getString("entry_id"),
                TrackedItemLotId.parse(rs.getString("lot_id")),
                TrackedItemLotId.parse(rs.getString("related_lot_id")),
                LotRelationType.fromToken(rs.getString("relation_type")),
                rs.getInt("lot_amount"),
                rs.getInt("related_amount"),
                Instant.ofEpochMilli(rs.getLong("occurred_at")),
                rs.getString("source"),
                rs.getString("idempotency_key")
        );
    }

    private LotCompatibilityFingerprint parseFingerprint(String serialized) {
        String[] parts = serialized.split("\\|", -1);
        if (parts.length < 12) {
            return LotCompatibilityFingerprint.builder().build();
        }
        return LotCompatibilityFingerprint.builder()
                .providerId(parts[0])
                .contentKeyId(parts[1])
                .material(parts[2])
                .damageValue(Integer.parseInt(parts[3]))
                .potionVariant(parts[4])
                .bookState(parts[5])
                .mapState(parts[6])
                .fireworkState(parts[7])
                .trimState(parts[8])
                .customModelData(Integer.parseInt(parts[10]))
                .providerMetadataHash(parts[11])
                .build();
    }
}
