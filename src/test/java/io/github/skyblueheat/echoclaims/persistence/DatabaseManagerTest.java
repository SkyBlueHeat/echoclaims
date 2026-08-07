package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.persistence.migration.SchemaMigrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsNestedDirectoryAndDatabase() throws Exception {
        DatabaseManager database = new DatabaseManager(
                tempDir.resolve("a/b/c/nested.db"));
        database.initialize();
        assertTrue(database.healthy());
        assertEquals(6, database.schemaVersion());
    }

    @Test
    void healthyReturnsFalseForInaccessiblePath() {
        DatabaseManager database = new DatabaseManager(
                Path.of("/dev/null/cannot-create.db"));
        assertFalse(database.healthy());
    }

    @Test
    void openConnectionFailsForInvalidPath() {
        DatabaseManager database = new DatabaseManager(
                Path.of("/dev/null/subdir/invalid.db"));
        assertFalse(database.healthy());
    }

    @Test
    void pragmasAreConfiguredCorrectly() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("pragma.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase());
            }

            try (ResultSet rs = statement.executeQuery("PRAGMA synchronous")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "NORMAL synchronous mode = 1");
            }

            try (ResultSet rs = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "foreign keys should be enforced");
            }

            try (ResultSet rs = statement.executeQuery("PRAGMA busy_timeout")) {
                assertTrue(rs.next());
                assertEquals(5000, rs.getInt(1), "busy timeout should be 5000ms");
            }
        }
    }

    @Test
    void foreignKeyEnforcementIsActive() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("fk.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE fk_parent (id INTEGER PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE fk_child (id INTEGER PRIMARY KEY, parent_id INTEGER NOT NULL, "
                    + "FOREIGN KEY (parent_id) REFERENCES fk_parent(id))");

            assertThrows(SQLException.class, () ->
                    statement.execute("INSERT INTO fk_child (id, parent_id) VALUES (1, 999)"));
        }
    }

    @Test
    void schemaVersionIsZeroBeforeInitialization() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("noversion.db"));

        assertEquals(0, database.schemaVersion(),
                "schemaVersion should be 0 before any migrations are applied");
    }

    @Test
    void jdbcUrlContainsAbsolutePath() {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("url.db"));
        String url = database.jdbcUrl();
        assertTrue(url.startsWith("jdbc:sqlite:"));
        assertTrue(url.contains("url.db"));
    }

    @Test
    void databasePathReturnsAbsolutePath() {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("path.db"));
        assertTrue(database.databasePath().isAbsolute());
    }

    @Test
    void reinitializationIsIdempotent() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("idempotent.db"));
        int first = database.initialize();
        int second = database.initialize();

        assertEquals(6, first);
        assertEquals(0, second);
        assertEquals(SchemaMigrator.latestVersion(), database.schemaVersion());
    }
}
