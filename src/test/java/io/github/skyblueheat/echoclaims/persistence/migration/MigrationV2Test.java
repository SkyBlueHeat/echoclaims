package io.github.skyblueheat.echoclaims.persistence.migration;

import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationV2Test {

    @TempDir
    Path tempDir;

    @Test
    void createsEvidenceTablesAndIndexes() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("evidence.db"));
        database.initialize();

        List<String> tables = new ArrayList<>();
        List<String> indexes = new ArrayList<>();
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT type, name FROM sqlite_master WHERE type IN ('table','index')")) {
            while (resultSet.next()) {
                String type = resultSet.getString(1);
                String name = resultSet.getString(2);
                if ("table".equals(type)) {
                    tables.add(name);
                } else {
                    indexes.add(name);
                }
            }
        }

        assertTrue(tables.contains("inventory_snapshots"));
        assertTrue(tables.contains("snapshot_items"));
        assertTrue(tables.contains("incidents"));

        assertTrue(indexes.contains("idx_inventory_snapshots_player_uuid"));
        assertTrue(indexes.contains("idx_inventory_snapshots_captured_at"));
        assertTrue(indexes.contains("idx_snapshot_items_snapshot_uuid"));
        assertTrue(indexes.contains("idx_incidents_player_uuid"));
        assertTrue(indexes.contains("idx_incidents_occurred_at"));
        assertTrue(indexes.contains("idx_incidents_incident_type"));
    }

    @Test
    void latestVersionIs2() {
        assertEquals(2, SchemaMigrator.latestVersion());
        assertEquals(2, SchemaMigrator.migrations().size());
    }

    @Test
    void snapshotItemsHasUniqueConstraintOnSnapshotUuidAndSlot() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("unique.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO inventory_snapshots (id, player_uuid, capture_reason, captured_at, "
                            + "world_id, game_mode, health, food_level, experience_level, "
                            + "total_experience, schema_version) "
                            + "VALUES ('test-1', '00000000-0000-0000-0000-000000000001', "
                            + "'PRE_DEATH', 1000, 'world', '', 0, 0, 0, 0, 1)");
            statement.execute(
                    "INSERT INTO snapshot_items (snapshot_uuid, slot, slot_type, material_key, "
                            + "amount, serialized_data, content_identity, score, display_name, "
                            + "damage, max_durability, enchantments) "
                            + "VALUES ('test-1', '0', 'INVENTORY', 'minecraft:stone', 1, '', '', 0, '', 0, 0, '')");

            boolean threw = false;
            try {
                statement.execute(
                        "INSERT INTO snapshot_items (snapshot_uuid, slot, slot_type, material_key, "
                                + "amount, serialized_data, content_identity, score, display_name, "
                                + "damage, max_durability, enchantments) "
                                + "VALUES ('test-1', '0', 'INVENTORY', 'minecraft:dirt', 1, '', '', 0, '', 0, 0, '')");
            } catch (Exception exception) {
                threw = true;
            }
            assertTrue(threw, "Duplicate (snapshot_uuid, slot) should be rejected");
        }
    }

    @Test
    void incidentsHasUniqueDeduplicationKey() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("dedup.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO inventory_snapshots (id, player_uuid, capture_reason, captured_at, "
                            + "world_id, game_mode, health, food_level, experience_level, "
                            + "total_experience, schema_version) "
                            + "VALUES ('snap-1', '00000000-0000-0000-0000-000000000001', "
                            + "'PRE_DEATH', 1000, 'world', '', 0, 0, 0, 0, 1)");
            statement.execute(
                    "INSERT INTO incidents (id, incident_type, player_uuid, occurred_at, "
                            + "world_id, cause, killer_entity_key, pre_event_snapshot_uuid, "
                            + "status, metadata, deduplication_key) "
                            + "VALUES ('inc-1', 'PLAYER_DEATH', "
                            + "'00000000-0000-0000-0000-000000000001', 1000, 'world', '', '', "
                            + "'snap-1', 'OPEN', '', 'dedup-key-1')");

            boolean threw = false;
            try {
                statement.execute(
                        "INSERT INTO incidents (id, incident_type, player_uuid, occurred_at, "
                                + "world_id, cause, killer_entity_key, pre_event_snapshot_uuid, "
                                + "status, metadata, deduplication_key) "
                                + "VALUES ('inc-2', 'PLAYER_DEATH', "
                                + "'00000000-0000-0000-0000-000000000001', 1000, 'world', '', '', "
                                + "'snap-1', 'OPEN', '', 'dedup-key-1')");
            } catch (Exception exception) {
                threw = true;
            }
            assertTrue(threw, "Duplicate deduplication_key should be rejected");
        }
    }
}
