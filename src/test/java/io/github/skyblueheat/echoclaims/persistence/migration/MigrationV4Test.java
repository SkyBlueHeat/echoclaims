package io.github.skyblueheat.echoclaims.persistence.migration;

import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MigrationV4Test {

    @TempDir
    Path tempDir;

    private void seedSnapshotAndIncident(Connection connection, String snapshotId,
                                         String incidentId, String playerUuid) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO inventory_snapshots (id, player_uuid, capture_reason, "
                    + "captured_at, world_id, x, y, z, game_mode, health, food_level, "
                    + "experience_level, total_experience, schema_version) "
                    + "VALUES ('" + snapshotId + "', '" + playerUuid + "', 'PRE_DEATH', "
                    + "10000, 'world', 0, 0, 0, 'SURVIVAL', 20, 20, 0, 0, 1)");

            stmt.execute("INSERT INTO incidents (id, incident_type, player_uuid, occurred_at, "
                    + "world_id, x, y, z, cause, killer_player_uuid, killer_entity_key, "
                    + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, metadata, "
                    + "deduplication_key) "
                    + "VALUES ('" + incidentId + "', 'PLAYER_DEATH', '" + playerUuid + "', "
                    + "10000, 'world', 0, 0, 0, 'FALL', NULL, '', '" + snapshotId + "', "
                    + "NULL, 'OPEN', '', 'dedup-" + incidentId + "')");
        }
    }

    @Test
    void createsPartialUniqueIndexForActiveClaims() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v4test.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_claims_active_unique'")) {
            assertTrue(rs.next(), "idx_claims_active_unique index should exist");
        }
    }

    @Test
    void partialUniqueIndexPreventsDuplicateActiveClaims() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v4dup.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {

            seedSnapshotAndIncident(connection, "snap-1", "inc-1", "p1");

            statement.execute("INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                    + "status, source, description, created_at, submitted_at, cancelled_at, "
                    + "version, metadata) "
                    + "VALUES ('c1', 'REF00001', 'inc-1', 'p1', 'DRAFT', 'PLAYER_COMMAND', '', "
                    + "10000, 0, 0, 0, '')");

            try {
                statement.execute("INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                        + "status, source, description, created_at, submitted_at, cancelled_at, "
                        + "version, metadata) "
                        + "VALUES ('c2', 'REF00002', 'inc-1', 'p1', 'DRAFT', 'PLAYER_COMMAND', '', "
                        + "10000, 0, 0, 0, '')");
                fail("Should have thrown due to partial unique index on active claims");
            } catch (java.sql.SQLException expected) {
                assertTrue(expected.getMessage().contains("UNIQUE constraint failed"),
                        "Expected UNIQUE constraint failure, got: " + expected.getMessage());
            }
        }
    }

    @Test
    void cancelledClaimsAllowNewActiveClaimForSameIncident() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v4cancel.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {

            seedSnapshotAndIncident(connection, "snap-2", "inc-2", "p2");

            statement.execute("INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                    + "status, source, description, created_at, submitted_at, cancelled_at, "
                    + "version, metadata) "
                    + "VALUES ('c3', 'REF00003', 'inc-2', 'p2', 'CANCELLED', 'PLAYER_COMMAND', '', "
                    + "10000, 0, 20000, 1, '')");

            statement.execute("INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                    + "status, source, description, created_at, submitted_at, cancelled_at, "
                    + "version, metadata) "
                    + "VALUES ('c4', 'REF00004', 'inc-2', 'p2', 'DRAFT', 'PLAYER_COMMAND', '', "
                    + "30000, 0, 0, 0, '')");

            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM claims WHERE incident_id='inc-2' AND player_uuid='p2'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }
}
