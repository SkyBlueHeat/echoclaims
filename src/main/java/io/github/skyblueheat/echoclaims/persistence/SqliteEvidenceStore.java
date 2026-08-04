package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite implementation of {@link EvidenceStore}.
 *
 * <p>Both {@link #insertDeathEvidence} and {@link #insertPostRespawnSnapshot} execute
 * all SQL statements inside a single transaction. If any statement fails, the entire
 * transaction is rolled back, guaranteeing no orphan snapshots or incidents.</p>
 */
public final class SqliteEvidenceStore implements EvidenceStore {

    private final DatabaseManager databaseManager;

    public SqliteEvidenceStore(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public void insertDeathEvidence(InventorySnapshot snapshot, Incident incident) throws SQLException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(incident, "incident");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertSnapshotRow(connection, snapshot);
                insertItems(connection, snapshot);
                insertIncidentRow(connection, incident);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void insertPostRespawnSnapshot(UUID incidentId, InventorySnapshot snapshot) throws SQLException {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(snapshot, "snapshot");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertSnapshotRow(connection, snapshot);
                insertItems(connection, snapshot);
                updatePostEventSnapshot(connection, incidentId, snapshot.id());
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static void insertSnapshotRow(Connection connection, InventorySnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO inventory_snapshots (id, player_uuid, capture_reason, captured_at, "
                        + "world_id, x, y, z, game_mode, health, food_level, experience_level, "
                        + "total_experience, schema_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, snapshot.id().toString());
            statement.setString(2, snapshot.playerUuid().toString());
            statement.setString(3, snapshot.captureReason().name());
            statement.setLong(4, snapshot.capturedAt());
            statement.setString(5, snapshot.worldId());
            if (snapshot.hasCoordinates()) {
                statement.setInt(6, snapshot.coordinates().x());
                statement.setInt(7, snapshot.coordinates().y());
                statement.setInt(8, snapshot.coordinates().z());
            } else {
                statement.setNull(6, java.sql.Types.INTEGER);
                statement.setNull(7, java.sql.Types.INTEGER);
                statement.setNull(8, java.sql.Types.INTEGER);
            }
            statement.setString(9, snapshot.gameMode());
            statement.setDouble(10, snapshot.health());
            statement.setInt(11, snapshot.foodLevel());
            statement.setInt(12, snapshot.experienceLevel());
            statement.setInt(13, snapshot.totalExperience());
            statement.setInt(14, snapshot.schemaVersion());
            statement.executeUpdate();
        }
    }

    private static void insertItems(Connection connection, InventorySnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO snapshot_items (snapshot_uuid, slot, slot_type, material_key, "
                        + "amount, serialized_data, content_identity, score, display_name, "
                        + "damage, max_durability, enchantments) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (SnapshotItem item : snapshot.inventoryItems()) {
                bindItem(statement, snapshot.id(), item, "INVENTORY");
                statement.addBatch();
            }
            for (SnapshotItem item : snapshot.armorItems()) {
                bindItem(statement, snapshot.id(), item, "ARMOR");
                statement.addBatch();
            }
            if (snapshot.offhandItem() != null) {
                bindItem(statement, snapshot.id(), snapshot.offhandItem(), "OFFHAND");
                statement.addBatch();
            }
            if (snapshot.cursorItem() != null) {
                bindItem(statement, snapshot.id(), snapshot.cursorItem(), "CURSOR");
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertIncidentRow(Connection connection, Incident incident) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO incidents (id, incident_type, player_uuid, occurred_at, "
                        + "world_id, x, y, z, cause, killer_player_uuid, killer_entity_key, "
                        + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, "
                        + "metadata, deduplication_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, incident.id().toString());
            statement.setString(2, incident.type().name());
            statement.setString(3, incident.playerUuid().toString());
            statement.setLong(4, incident.occurredAt());
            statement.setString(5, incident.worldId());
            if (incident.hasCoordinates()) {
                statement.setInt(6, incident.coordinates().x());
                statement.setInt(7, incident.coordinates().y());
                statement.setInt(8, incident.coordinates().z());
            } else {
                statement.setNull(6, java.sql.Types.INTEGER);
                statement.setNull(7, java.sql.Types.INTEGER);
                statement.setNull(8, java.sql.Types.INTEGER);
            }
            statement.setString(9, incident.cause());
            if (incident.hasKillerPlayer()) {
                statement.setString(10, incident.killerPlayerUuid().toString());
            } else {
                statement.setNull(10, java.sql.Types.VARCHAR);
            }
            statement.setString(11, incident.killerEntityKey());
            statement.setString(12, incident.preEventSnapshotUuid().toString());
            if (incident.hasPostEventSnapshot()) {
                statement.setString(13, incident.postEventSnapshotUuid().toString());
            } else {
                statement.setNull(13, java.sql.Types.VARCHAR);
            }
            statement.setString(14, incident.status().name());
            statement.setString(15, MapCodec.encodeStringMap(incident.metadata()));
            statement.setString(16, incident.deduplicationKey());
            statement.executeUpdate();
        }
    }

    private static void updatePostEventSnapshot(Connection connection, UUID incidentId, UUID snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE incidents SET post_event_snapshot_uuid = ? WHERE id = ?")) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, incidentId.toString());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new SQLException("No incident found with id " + incidentId + " to link post-event snapshot");
            }
        }
    }

    private static void bindItem(PreparedStatement statement, UUID snapshotUuid,
                                  SnapshotItem item, String slotType) throws SQLException {
        statement.setString(1, snapshotUuid.toString());
        statement.setString(2, item.slot());
        statement.setString(3, slotType);
        statement.setString(4, item.materialKey());
        statement.setInt(5, item.amount());
        statement.setString(6, item.serializedData());
        statement.setString(7, item.contentIdentity());
        statement.setInt(8, item.score());
        statement.setString(9, item.displayName());
        statement.setInt(10, item.damage());
        statement.setInt(11, item.maxDurability());
        statement.setString(12, MapCodec.encodeIntMap(item.enchantments()));
    }
}
