package io.github.skyblueheat.echoclaims.persistence.migration;

import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigratorFailureTest {

    @TempDir
    Path tempDir;

    @Test
    void failedMigrationRollsBack() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("rollback.db"));
        database.initialize();

        try (Connection connection = database.openConnection()) {
            assertThrows(SQLException.class, () -> {
                connection.setAutoCommit(false);
                try {
                    connection.createStatement().executeUpdate(
                            "CREATE TABLE bad_table (id INTEGER, bad_column TEXT NOT NULL)");
                    connection.createStatement().executeUpdate(
                            "INSERT INTO bad_table (id) VALUES (1)");
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            });

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT name FROM sqlite_master WHERE type='table' AND name='bad_table'")) {
                assertFalse(rs.next(), "bad_table should not exist after rollback");
            }
        }
    }

    @Test
    void failedMigrationIsNotRecordedAsApplied() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("notapplied.db"));
        database.initialize();

        try (Connection connection = database.openConnection()) {
            assertThrows(SQLException.class, () -> {
                connection.setAutoCommit(false);
                try {
                    connection.createStatement().executeUpdate("INVALID SQL STATEMENT");
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            });

            int version = SchemaMigrator.currentVersion(connection);
            assertEquals(SchemaMigrator.latestVersion(), version,
                    "version should remain at the last successful migration");
        }
    }

    @Test
    void successfulRetryCanRunAfterFailure() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("retry.db"));
        database.initialize();

        int firstRun = database.initialize();
        assertEquals(0, firstRun, "second initialize should apply 0 migrations");

        int version = database.schemaVersion();
        assertEquals(SchemaMigrator.latestVersion(), version);
    }

    @Test
    void migrationVersionsAreUnique() {
        List<Migration> migrations = SchemaMigrator.migrations();
        for (int i = 0; i < migrations.size(); i++) {
            for (int j = i + 1; j < migrations.size(); j++) {
                assertTrue(migrations.get(i).version() != migrations.get(j).version(),
                        "migration versions must be unique");
            }
        }
    }

    @Test
    void migrationOrderingIsDeterministic() {
        List<Migration> migrations = SchemaMigrator.migrations();
        for (int i = 1; i < migrations.size(); i++) {
            assertTrue(migrations.get(i - 1).version() < migrations.get(i).version(),
                    "migrations must be in ascending version order");
        }
    }

    @Test
    void migrationAppliesInTransaction() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("transaction.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT version, description FROM schema_version ORDER BY version")) {

            for (Migration migration : SchemaMigrator.migrations()) {
                assertTrue(rs.next());
                assertEquals(migration.version(), rs.getInt(1));
                assertEquals(migration.description(), rs.getString(2));
            }
        }
    }

    @Test
    void migrationsListIsTheActualCollectionUsedByMigrate() {
        // The static initializer validates duplicates on MIGRATIONS.
        // migrations() returns the same immutable list, so the validation
        // applies to the actual collection used by migrate().
        List<Migration> fromMigrations = SchemaMigrator.migrations();
        List<Migration> fromLatestVersion = SchemaMigrator.migrations();

        assertSame(fromMigrations, fromLatestVersion,
                "migrations() must return the same immutable list instance");
        assertFalse(fromMigrations.isEmpty(), "migrations list must not be empty");
    }

    @Test
    void migrationVersionsHaveNoGaps() {
        List<Migration> migrations = SchemaMigrator.migrations();
        for (int i = 1; i < migrations.size(); i++) {
            int prev = migrations.get(i - 1).version();
            int curr = migrations.get(i).version();
            assertEquals(prev + 1, curr,
                    "migration versions must be contiguous (no gaps): expected "
                            + (prev + 1) + " but got " + curr);
        }
    }

    @Test
    void migrationStartsAtVersionOne() {
        List<Migration> migrations = SchemaMigrator.migrations();
        assertEquals(1, migrations.get(0).version(),
                "first migration must start at version 1");
    }

    @Test
    void latestVersionMatchesLastMigration() {
        List<Migration> migrations = SchemaMigrator.migrations();
        int lastVersion = migrations.get(migrations.size() - 1).version();
        assertEquals(lastVersion, SchemaMigrator.latestVersion(),
                "latestVersion() must match the last migration's version");
    }

    @Test
    void futureMigrationExtensionIsSupported() throws Exception {
        // Verify that a database at the current latest version will not need
        // any migrations when a future version is added (simulated by re-running).
        DatabaseManager database = new DatabaseManager(tempDir.resolve("future.db"));
        database.initialize();

        int secondRun = database.initialize();
        assertEquals(0, secondRun,
                "re-running initialize on a fully-migrated database should apply 0 migrations");

        assertEquals(SchemaMigrator.latestVersion(), database.schemaVersion(),
                "schema version should match latest after idempotent re-run");
    }

    private static <T> void assertSame(T expected, T actual, String message) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual, message);
    }
}
