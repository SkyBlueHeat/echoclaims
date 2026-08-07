package io.github.skyblueheat.echoclaims.persistence.migration;

import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationV6Test {

    @TempDir
    Path tempDir;

    @Test
    void createsRefundsTable() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v6_refunds.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='refunds'")) {
            assertTrue(rs.next(), "refunds table should exist");
        }
    }

    @Test
    void createsRefundItemsTable() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v6_items.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='refund_items'")) {
            assertTrue(rs.next(), "refund_items table should exist");
        }
    }

    @Test
    void createsRefundAuditEntriesTable() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v6_audit.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='refund_audit_entries'")) {
            assertTrue(rs.next(), "refund_audit_entries table should exist");
        }
    }

    @Test
    void refundsTableHasUniqueConstraintOnReviewId() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v6_unique.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO inventory_snapshots (id, player_uuid, capture_reason, "
                    + "captured_at, world_id, x, y, z, game_mode, health, food_level, "
                    + "experience_level, total_experience, schema_version) "
                    + "VALUES ('snap-1', 'player-1', 'PRE_DEATH', 10000, 'world', 0, 0, 0, "
                    + "'SURVIVAL', 20, 20, 0, 0, 1)");

            statement.execute("INSERT INTO incidents (id, incident_type, player_uuid, occurred_at, "
                    + "world_id, x, y, z, cause, killer_player_uuid, killer_entity_key, "
                    + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, metadata, "
                    + "deduplication_key) "
                    + "VALUES ('inc-1', 'PLAYER_DEATH', 'player-1', 10000, 'world', 0, 0, 0, "
                    + "'FALL', NULL, '', 'snap-1', NULL, 'OPEN', '', 'dedup-1')");

            statement.execute("INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                    + "status, source, description, created_at, submitted_at, cancelled_at, "
                    + "version, metadata) "
                    + "VALUES ('claim-1', 'REF-001', 'inc-1', 'player-1', 'RESOLVED', "
                    + "'STAFF_REVIEW', '', 10000, 10000, 0, 0, '')");

            statement.execute("INSERT INTO claim_reviews (id, claim_id, assigned_reviewer_uuid, "
                    + "review_state, final_outcome, final_summary, started_at, updated_at, "
                    + "finalized_at, version, metadata) "
                    + "VALUES ('review-1', 'claim-1', 'staff-1', 'FINALIZED', 'APPROVED', "
                    + "'', 10000, 10000, 10001, 0, '')");

            statement.execute("INSERT INTO refunds (id, claim_id, review_id, player_uuid, "
                    + "status, created_at, completed_at, version, metadata) "
                    + "VALUES ('refund-1', 'claim-1', 'review-1', 'player-1', 'PENDING', "
                    + "10001, 0, 0, '')");

            boolean threw = false;
            try {
                statement.execute("INSERT INTO refunds (id, claim_id, review_id, player_uuid, "
                        + "status, created_at, completed_at, version, metadata) "
                        + "VALUES ('refund-2', 'claim-1', 'review-1', 'player-1', 'PENDING', "
                        + "10001, 0, 0, '')");
            } catch (SQLException e) {
                threw = true;
            }
            assertTrue(threw, "Duplicate review_id should be rejected by unique constraint");
        }
    }

    @Test
    void refundItemsTableHasForeignKeyToRefunds() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v6_fk.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");

            boolean threw = false;
            try {
                statement.execute("INSERT INTO refund_items (id, refund_id, claim_id, review_id, "
                        + "player_uuid, evidence_item_reference, source_snapshot_id, material_key, "
                        + "refundable_quantity, delivered_quantity, serialized_item_data, status, "
                        + "failure_reason, created_at, updated_at, version) "
                        + "VALUES ('item-1', 'nonexistent-refund', 'claim-1', 'review-1', "
                        + "'player-1', 'ref:0', 'snap-1', 'diamond', 1, 0, 'data', 'PENDING', "
                        + "'', 10000, 10000, 0)");
            } catch (SQLException e) {
                threw = true;
            }
            assertTrue(threw, "Foreign key to refunds should be enforced");
        }
    }

    @Test
    void refundAuditEntriesTableHasForeignKeyToRefunds() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v6_audit_fk.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");

            boolean threw = false;
            try {
                statement.execute("INSERT INTO refund_audit_entries (id, refund_id, claim_id, "
                        + "review_id, player_uuid, action, quantity, reason, recorded_at, "
                        + "metadata, version) "
                        + "VALUES ('audit-1', 'nonexistent-refund', 'claim-1', 'review-1', "
                        + "'player-1', 'CREATED', 0, '', 10000, '', 0)");
            } catch (SQLException e) {
                threw = true;
            }
            assertTrue(threw, "Foreign key to refunds should be enforced for audit entries");
        }
    }

    @Test
    void expectedIndexesExist() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("v6_indexes.db"));
        database.initialize();

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_refund%'")) {

            java.util.Set<String> indexes = new java.util.HashSet<>();
            while (rs.next()) {
                indexes.add(rs.getString("name"));
            }

            assertTrue(indexes.contains("idx_refunds_claim_id"));
            assertTrue(indexes.contains("idx_refunds_player_uuid"));
            assertTrue(indexes.contains("idx_refunds_status"));
            assertTrue(indexes.contains("idx_refund_items_refund_id"));
            assertTrue(indexes.contains("idx_refund_items_status"));
            assertTrue(indexes.contains("idx_refund_audit_refund_id"));
        }
    }

    @Test
    void latestVersionIs6() {
        assertEquals(6, SchemaMigrator.latestVersion());
        assertEquals(6, SchemaMigrator.migrations().size());
    }
}
