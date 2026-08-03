package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of {@link InventorySnapshotRepository}.
 *
 * <p>Every method blocks on disk I/O and must therefore be called from the EchoClaims
 * writer thread, never from the server thread. The {@link #insert} method inserts the
 * snapshot row and all item rows in a single transaction.</p>
 */
public final class SqliteInventorySnapshotRepository implements InventorySnapshotRepository {

    private final DatabaseManager databaseManager;

    public SqliteInventorySnapshotRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public void insert(InventorySnapshot snapshot) throws SQLException {
        Objects.requireNonNull(snapshot, "snapshot");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertSnapshotRow(connection, snapshot);
                insertItems(connection, snapshot);
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
    public Optional<InventorySnapshot> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = databaseManager.openConnection()) {
            Optional<InventorySnapshot> snapshot = querySnapshot(connection, id);
            return snapshot;
        }
    }

    @Override
    public List<InventorySnapshot> findRecentByPlayer(UUID playerUuid, int limit) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection()) {
            List<UUID> ids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM inventory_snapshots WHERE player_uuid = ? "
                            + "ORDER BY captured_at DESC LIMIT ?")) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, safeLimit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(UUID.fromString(resultSet.getString(1)));
                    }
                }
            }
            List<InventorySnapshot> snapshots = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                querySnapshot(connection, id).ifPresent(snapshots::add);
            }
            return List.copyOf(snapshots);
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM inventory_snapshots");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private void insertSnapshotRow(Connection connection, InventorySnapshot snapshot) throws SQLException {
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

    private void insertItems(Connection connection, InventorySnapshot snapshot) throws SQLException {
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

    private Optional<InventorySnapshot> querySnapshot(Connection connection, UUID id) throws SQLException {
        InventorySnapshotData data;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, player_uuid, capture_reason, captured_at, world_id, x, y, z, "
                        + "game_mode, health, food_level, experience_level, total_experience, "
                        + "schema_version FROM inventory_snapshots WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                data = readSnapshotData(resultSet);
            }
        }

        List<SnapshotItem> inventory = new ArrayList<>();
        List<SnapshotItem> armor = new ArrayList<>();
        SnapshotItem offhand = null;
        SnapshotItem cursor = null;

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT slot, slot_type, material_key, amount, serialized_data, "
                        + "content_identity, score, display_name, damage, max_durability, "
                        + "enchantments FROM snapshot_items WHERE snapshot_uuid = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    SnapshotItem item = readItem(resultSet);
                    String slotType = resultSet.getString("slot_type");
                    switch (slotType) {
                        case "INVENTORY" -> inventory.add(item);
                        case "ARMOR" -> armor.add(item);
                        case "OFFHAND" -> offhand = item;
                        case "CURSOR" -> cursor = item;
                        default -> { /* skip unknown slot types */ }
                    }
                }
            }
        }

        return Optional.of(new InventorySnapshot(
                data.id(),
                data.playerUuid(),
                data.captureReason(),
                data.capturedAt(),
                data.worldId(),
                data.coordinates(),
                data.gameMode(),
                data.health(),
                data.foodLevel(),
                data.experienceLevel(),
                data.totalExperience(),
                List.copyOf(inventory),
                List.copyOf(armor),
                offhand,
                cursor,
                data.schemaVersion()
        ));
    }

    private static InventorySnapshotData readSnapshotData(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
        CaptureReason reason = CaptureReason.valueOf(resultSet.getString("capture_reason"));
        long capturedAt = resultSet.getLong("captured_at");
        String worldId = resultSet.getString("world_id");
        Coordinates coordinates = null;
        int x = resultSet.getInt("x");
        if (!resultSet.wasNull()) {
            int y = resultSet.getInt("y");
            int z = resultSet.getInt("z");
            coordinates = new Coordinates(x, y, z);
        }
        String gameMode = resultSet.getString("game_mode");
        double health = resultSet.getDouble("health");
        int foodLevel = resultSet.getInt("food_level");
        int experienceLevel = resultSet.getInt("experience_level");
        int totalExperience = resultSet.getInt("total_experience");
        int schemaVersion = resultSet.getInt("schema_version");

        return new InventorySnapshotData(
                id, playerUuid, reason, capturedAt, worldId, coordinates,
                gameMode, health, foodLevel, experienceLevel, totalExperience, schemaVersion
        );
    }

    private static SnapshotItem readItem(ResultSet resultSet) throws SQLException {
        return new SnapshotItem(
                resultSet.getString("slot"),
                resultSet.getString("material_key"),
                resultSet.getInt("amount"),
                resultSet.getString("serialized_data"),
                resultSet.getString("content_identity"),
                resultSet.getInt("score"),
                resultSet.getString("display_name"),
                resultSet.getInt("damage"),
                resultSet.getInt("max_durability"),
                MapCodec.decodeIntMap(resultSet.getString("enchantments"))
        );
    }

    private record InventorySnapshotData(
            UUID id,
            UUID playerUuid,
            CaptureReason captureReason,
            long capturedAt,
            String worldId,
            Coordinates coordinates,
            String gameMode,
            double health,
            int foodLevel,
            int experienceLevel,
            int totalExperience,
            int schemaVersion
    ) {
    }
}
