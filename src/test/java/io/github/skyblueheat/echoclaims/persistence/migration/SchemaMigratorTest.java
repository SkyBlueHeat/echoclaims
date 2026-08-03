package io.github.skyblueheat.echoclaims.persistence.migration;

import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
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
    void createsAuditRecordsTableAndIndexes() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("audit.db"));
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

        assertTrue(tables.contains("audit_records"));
        assertTrue(indexes.contains("idx_audit_records_recorded_at"));
        assertTrue(indexes.contains("idx_audit_records_category"));
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
}
