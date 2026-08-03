package dev.worldecho.persistence.migration;

import dev.worldecho.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesEmptyDatabaseToLatestVersion() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("nested/empty.db"));

        int applied = database.initialize();

        assertEquals(SchemaMigrator.migrations().size(), applied);
        assertEquals(SchemaMigrator.latestVersion(), database.schemaVersion());
        assertTrue(Files.exists(tempDir.resolve("nested/empty.db")));
        assertTrue(database.healthy());
    }

    @Test
    void reRunningMigrationsIsIdempotent() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("repeat.db"));
        database.initialize();

        assertEquals(0, database.initialize());
        assertEquals(SchemaMigrator.latestVersion(), database.schemaVersion());
    }

    @Test
    void createsIndexesForFrequentLookups() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("indexes.db"));
        database.initialize();

        List<String> indexes = new ArrayList<>();
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='index'")) {
            while (resultSet.next()) {
                indexes.add(resultSet.getString(1));
            }
        }

        assertTrue(indexes.contains("idx_story_events_occurred_at"));
        assertTrue(indexes.contains("idx_story_events_player_id"));
        assertTrue(indexes.contains("idx_story_events_event_type"));
    }

    @Test
    void createsTrackedItemsAndLedgerTablesAndIndexes() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v2.db"));
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

        assertTrue(tables.contains("tracked_items"));
        assertTrue(tables.contains("item_ownership_ledger"));
        assertTrue(indexes.contains("idx_tracked_items_content_key"));
        assertTrue(indexes.contains("idx_ledger_item_seq_desc"));
        assertTrue(indexes.contains("idx_ledger_new_subject"));
        assertTrue(indexes.contains("idx_ledger_occurred_at"));
    }

    @Test
    void recordsAppliedVersionsWithDescriptions() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("versions.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version, description FROM schema_version ORDER BY version")) {
            for (Migration migration : SchemaMigrator.migrations()) {
                assertTrue(resultSet.next());
                assertEquals(migration.version(), resultSet.getInt(1));
                assertEquals(migration.description(), resultSet.getString(2));
            }
        }
    }

    @Test
    void createsLotTablesAndIndexesInV3() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v3.db"));
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

        assertTrue(tables.contains("tracked_item_lots"));
        assertTrue(tables.contains("lot_lineage"));
        assertTrue(tables.contains("item_lot_ownership_ledger"));
        assertTrue(indexes.contains("idx_tracked_lots_fingerprint"));
        assertTrue(indexes.contains("idx_tracked_lots_fingerprint_owner"));
        assertTrue(indexes.contains("idx_tracked_lots_content_key"));
        assertTrue(indexes.contains("idx_tracked_lots_owner_fp"));
        assertTrue(indexes.contains("idx_tracked_lots_owner_fp_unique"));
        assertTrue(indexes.contains("idx_lot_lineage_lot_id"));
        assertTrue(indexes.contains("idx_lot_lineage_related_lot"));
        assertTrue(indexes.contains("idx_lot_ledger_lot_seq_desc"));
        assertTrue(indexes.contains("idx_lot_ledger_new_subject"));
        assertEquals(4, database.schemaVersion());
    }
}
