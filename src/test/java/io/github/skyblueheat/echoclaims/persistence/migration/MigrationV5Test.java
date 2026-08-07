package io.github.skyblueheat.echoclaims.persistence.migration;

import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MigrationV5Test {

    @TempDir
    Path tempDir;

    private void seedClaim(Connection connection, String claimId, String incidentId,
                           String playerUuid, String publicRef) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO inventory_snapshots (id, player_uuid, capture_reason, "
                    + "captured_at, world_id, x, y, z, game_mode, health, food_level, "
                    + "experience_level, total_experience, schema_version) "
                    + "VALUES ('snap-" + claimId + "', '" + playerUuid + "', 'PRE_DEATH', "
                    + "10000, 'world', 0, 0, 0, 'SURVIVAL', 20, 20, 0, 0, 1)");

            stmt.execute("INSERT INTO incidents (id, incident_type, player_uuid, occurred_at, "
                    + "world_id, x, y, z, cause, killer_player_uuid, killer_entity_key, "
                    + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, metadata, "
                    + "deduplication_key) "
                    + "VALUES ('" + incidentId + "', 'PLAYER_DEATH', '" + playerUuid + "', "
                    + "10000, 'world', 0, 0, 0, 'FALL', NULL, '', 'snap-" + claimId + "', "
                    + "NULL, 'OPEN', '', 'dedup-" + incidentId + "')");

            stmt.execute("INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                    + "status, source, description, created_at, submitted_at, cancelled_at, "
                    + "version, metadata) "
                    + "VALUES ('" + claimId + "', '" + publicRef + "', '" + incidentId + "', "
                    + "'" + playerUuid + "', 'SUBMITTED', 'PLAYER_COMMAND', '', "
                    + "10000, 10000, 0, 0, '')");
        }
    }

    @Test
    void createsClaimReviewsTable() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v5tables.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='claim_reviews'")) {
            assertTrue(rs.next(), "claim_reviews table should exist");
        }
    }

    @Test
    void createsClaimReviewCommentsTable() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v5comments.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='claim_review_comments'")) {
            assertTrue(rs.next(), "claim_review_comments table should exist");
        }
    }

    @Test
    void createsClaimReviewItemDecisionsTable() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v5decisions.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='claim_review_item_decisions'")) {
            assertTrue(rs.next(), "claim_review_item_decisions table should exist");
        }
    }

    @Test
    void claimReviewsHasUniqueClaimIdConstraint() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v5unique.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            seedClaim(connection, "c1", "inc-1", "p1", "REF00001");

            statement.execute("INSERT INTO claim_reviews (id, claim_id, assigned_reviewer_uuid, "
                    + "review_state, final_outcome, final_summary, started_at, updated_at, "
                    + "finalized_at, version, metadata) "
                    + "VALUES ('r1', 'c1', 'staff-1', 'OPEN', NULL, '', 10000, 10000, 0, 0, '')");

            try {
                statement.execute("INSERT INTO claim_reviews (id, claim_id, assigned_reviewer_uuid, "
                        + "review_state, final_outcome, final_summary, started_at, updated_at, "
                        + "finalized_at, version, metadata) "
                        + "VALUES ('r2', 'c1', 'staff-2', 'OPEN', NULL, '', 10000, 10000, 0, 0, '')");
                fail("Should have thrown due to UNIQUE(claim_id) constraint");
            } catch (java.sql.SQLException expected) {
                assertTrue(expected.getMessage().contains("UNIQUE constraint failed"),
                        "Expected UNIQUE constraint failure, got: " + expected.getMessage());
            }
        }
    }

    @Test
    void itemDecisionsHasUniqueReviewIdAndEvidenceRef() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v5itemunique.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            seedClaim(connection, "c2", "inc-2", "p2", "REF00002");

            statement.execute("INSERT INTO claim_reviews (id, claim_id, assigned_reviewer_uuid, "
                    + "review_state, final_outcome, final_summary, started_at, updated_at, "
                    + "finalized_at, version, metadata) "
                    + "VALUES ('r3', 'c2', 'staff-3', 'OPEN', NULL, '', 10000, 10000, 0, 0, '')");

            statement.execute("INSERT INTO claim_review_item_decisions (id, review_id, claim_id, "
                    + "evidence_item_reference, source_snapshot_id, original_quantity, "
                    + "approved_quantity, outcome, reason_code, staff_note, "
                    + "created_at, updated_at, version, metadata) "
                    + "VALUES ('d1', 'r3', 'c2', 'snap-1:0', 'snap-1', 10, 10, "
                    + "'APPROVED', 'EVIDENCE_CONFIRMS_LOSS', '', 10000, 10000, 0, '')");

            try {
                statement.execute("INSERT INTO claim_review_item_decisions (id, review_id, claim_id, "
                        + "evidence_item_reference, source_snapshot_id, original_quantity, "
                        + "approved_quantity, outcome, reason_code, staff_note, "
                        + "created_at, updated_at, version, metadata) "
                        + "VALUES ('d2', 'r3', 'c2', 'snap-1:0', 'snap-1', 10, 5, "
                        + "'PARTIALLY_APPROVED', 'EVIDENCE_PARTIALLY_CONFIRMS_LOSS', '', 10000, 10000, 0, '')");
                fail("Should have thrown due to UNIQUE(review_id, evidence_item_reference)");
            } catch (java.sql.SQLException expected) {
                assertTrue(expected.getMessage().contains("UNIQUE constraint failed"),
                        "Expected UNIQUE constraint failure, got: " + expected.getMessage());
            }
        }
    }

    @Test
    void reviewTablesHaveForeignKeyToClaims() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v5fk.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            try {
                statement.execute("INSERT INTO claim_reviews (id, claim_id, "
                        + "assigned_reviewer_uuid, review_state, final_outcome, final_summary, "
                        + "started_at, updated_at, finalized_at, version, metadata) "
                        + "VALUES ('r-fk', 'nonexistent-claim', 'staff', 'OPEN', NULL, '', "
                        + "10000, 10000, 0, 0, '')");
                fail("Should have thrown due to foreign key constraint on claim_id");
            } catch (java.sql.SQLException expected) {
                assertTrue(expected.getMessage().contains("FOREIGN KEY constraint failed"),
                        "Expected FK constraint failure, got: " + expected.getMessage());
            }
        }
    }

    @Test
    void createsExpectedIndexes() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v5indexes.db"));
        database.initialize();

        String[] expectedIndexes = {
                "idx_claim_reviews_claim_id",
                "idx_claim_reviews_reviewer",
                "idx_claim_reviews_state",
                "idx_claim_reviews_finalized_at",
                "idx_review_comments_claim_id",
                "idx_review_comments_review_id",
                "idx_review_comments_claim_created",
                "idx_review_comments_visibility",
                "idx_review_decisions_review_id",
                "idx_review_decisions_claim_id",
                "idx_review_decisions_evidence_ref"
        };

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            for (String indexName : expectedIndexes) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='index' AND name='" + indexName + "'")) {
                    assertTrue(rs.next(), "Index " + indexName + " should exist");
                }
            }
        }
    }

    @Test
    void latestVersionIs5() {
        assertEquals(5, SchemaMigrator.latestVersion());
        assertEquals(5, SchemaMigrator.migrations().size());
    }
}
