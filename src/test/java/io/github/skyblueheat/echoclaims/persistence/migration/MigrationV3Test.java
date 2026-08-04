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

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationV3Test {

    @TempDir
    Path tempDir;

    @Test
    void createsClaimTablesAndIndexes() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("claims.db"));
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

        assertTrue(tables.contains("claims"));
        assertTrue(tables.contains("claim_audit_entries"));

        assertTrue(indexes.contains("idx_claims_player_uuid"));
        assertTrue(indexes.contains("idx_claims_status"));
        assertTrue(indexes.contains("idx_claims_incident_id"));
        assertTrue(indexes.contains("idx_claims_created_at"));
        assertTrue(indexes.contains("idx_claim_audit_claim_id"));
        assertTrue(indexes.contains("idx_claim_audit_recorded_at"));
    }

    @Test
    void claimsTableHasUniquePublicReference() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("unique_ref.db"));
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
                            + "'snap-1', 'OPEN', '', 'dedup-1')");
            statement.execute(
                    "INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                            + "status, source, description, created_at, submitted_at, "
                            + "cancelled_at, version, metadata) "
                            + "VALUES ('claim-1', 'DUPREF01', 'inc-1', "
                            + "'00000000-0000-0000-0000-000000000001', 'DRAFT', 'PLAYER_COMMAND', "
                            + "'', 1000, 0, 0, 0, '')");

            boolean threw = false;
            try {
                statement.execute(
                        "INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                                + "status, source, description, created_at, submitted_at, "
                                + "cancelled_at, version, metadata) "
                                + "VALUES ('claim-2', 'DUPREF01', 'inc-1', "
                                + "'00000000-0000-0000-0000-000000000001', 'DRAFT', 'PLAYER_COMMAND', "
                                + "'', 1000, 0, 0, 0, '')");
            } catch (Exception exception) {
                threw = true;
            }
            assertTrue(threw, "Duplicate public_reference should be rejected");
        }
    }

    @Test
    void claimAuditEntriesHasForeignKeyToClaims() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("fk_audit.db"));
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
                            + "'snap-1', 'OPEN', '', 'dedup-1')");
            statement.execute(
                    "INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                            + "status, source, description, created_at, submitted_at, "
                            + "cancelled_at, version, metadata) "
                            + "VALUES ('claim-1', 'REF00001', 'inc-1', "
                            + "'00000000-0000-0000-0000-000000000001', 'DRAFT', 'PLAYER_COMMAND', "
                            + "'', 1000, 0, 0, 0, '')");
            statement.execute(
                    "INSERT INTO claim_audit_entries (id, claim_id, actor_type, actor_uuid, "
                            + "action, reason, recorded_at, claim_version, metadata) "
                            + "VALUES ('audit-1', 'claim-1', 'PLAYER', "
                            + "'00000000-0000-0000-0000-000000000001', 'CREATED', '', 1000, 0, '')");

            boolean threw = false;
            try {
                statement.execute(
                        "INSERT INTO claim_audit_entries (id, claim_id, actor_type, actor_uuid, "
                                + "action, reason, recorded_at, claim_version, metadata) "
                                + "VALUES ('audit-2', 'nonexistent-claim', 'PLAYER', "
                                + "'00000000-0000-0000-0000-000000000001', 'CREATED', '', 1000, 0, '')");
            } catch (Exception exception) {
                threw = true;
            }
            assertTrue(threw, "Audit entry with nonexistent claim_id should be rejected by FK");
        }
    }
}
