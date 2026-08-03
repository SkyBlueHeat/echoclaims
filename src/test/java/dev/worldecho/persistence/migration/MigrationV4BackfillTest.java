package dev.worldecho.persistence.migration;

import dev.worldecho.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests migration v4 backfill behavior: converting legacy {@code created_by_subject}
 * to {@code owner_type}, {@code owner_stable_id}, {@code owner_display_snapshot},
 * resolving duplicate legacy rows while preserving dependent references, and
 * ensuring idempotent re-run.
 *
 * <p>Legacy format: {@code player:<canonical-uuid>:<display-name>}
 * The migration extracts the 36-character UUID via
 * {@code substr(created_by_subject, 8, 36)} and the display name via
 * {@code substr(created_by_subject, 45)}.
 */
class MigrationV4BackfillTest {

    @TempDir
    Path tempDir;

    /**
     * Creates a database at schema v3 (only migrations 1-3 applied) with a lot row
     * using the legacy format (no owner_* columns).
     */
    private DatabaseManager createV3Database(String fileName, String createdBySubject,
                                              String fingerprint, String lotId, int amount) throws Exception {
        Path dbPath = tempDir.resolve(fileName);
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, description TEXT NOT NULL, applied_at INTEGER NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS story_events (id TEXT PRIMARY KEY, event_type TEXT NOT NULL, occurred_at INTEGER NOT NULL, world_id TEXT, x INTEGER, y INTEGER, z INTEGER, player_id TEXT, actor_provider TEXT, actor_content_id TEXT, actor_runtime_id TEXT, item_provider TEXT, item_content_id TEXT, item_snapshot TEXT, details TEXT NOT NULL)");
            stmt.execute("INSERT INTO schema_version VALUES (1, 'story event memory kernel', 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS tracked_items (item_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, content_key TEXT NOT NULL, provider_id TEXT NOT NULL, initial_material TEXT NOT NULL, initial_custom_name TEXT NOT NULL DEFAULT '', initial_value_score INTEGER, tracking_reason TEXT NOT NULL DEFAULT '', created_by_subject TEXT NOT NULL DEFAULT '')");
            stmt.execute("CREATE TABLE IF NOT EXISTS item_ownership_ledger (entry_id TEXT PRIMARY KEY, item_id TEXT NOT NULL, sequence_number INTEGER NOT NULL, previous_subject_type TEXT, previous_subject_id TEXT, previous_subject_display TEXT, new_subject_type TEXT NOT NULL, new_subject_id TEXT NOT NULL, new_subject_display TEXT NOT NULL DEFAULT '', transition_reason TEXT NOT NULL, occurred_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL, source TEXT NOT NULL DEFAULT '', story_event_id TEXT NOT NULL DEFAULT '', idempotency_key TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', UNIQUE(item_id, sequence_number), UNIQUE(item_id, idempotency_key), FOREIGN KEY(item_id) REFERENCES tracked_items(item_id))");
            stmt.execute("INSERT INTO schema_version VALUES (2, 'tracked item identity and ownership ledger', 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS tracked_item_lots (lot_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, content_key TEXT NOT NULL, provider_id TEXT NOT NULL DEFAULT '', material TEXT NOT NULL DEFAULT '', fingerprint TEXT NOT NULL, initial_amount INTEGER NOT NULL DEFAULT 0, current_amount INTEGER NOT NULL DEFAULT 0, tracking_reason TEXT NOT NULL DEFAULT '', created_by_subject TEXT NOT NULL DEFAULT '')");
            stmt.execute("CREATE TABLE IF NOT EXISTS lot_lineage (entry_id TEXT PRIMARY KEY, lot_id TEXT NOT NULL, related_lot_id TEXT NOT NULL, relation_type TEXT NOT NULL, lot_amount INTEGER NOT NULL DEFAULT 0, related_amount INTEGER NOT NULL DEFAULT 0, occurred_at INTEGER NOT NULL, source TEXT NOT NULL DEFAULT '', idempotency_key TEXT NOT NULL DEFAULT '', UNIQUE(lot_id, idempotency_key), FOREIGN KEY(lot_id) REFERENCES tracked_item_lots(lot_id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS item_lot_ownership_ledger (entry_id TEXT PRIMARY KEY, lot_id TEXT NOT NULL, sequence_number INTEGER NOT NULL, previous_subject_type TEXT, previous_subject_id TEXT, previous_subject_display TEXT, new_subject_type TEXT NOT NULL, new_subject_id TEXT NOT NULL, new_subject_display TEXT NOT NULL DEFAULT '', transition_reason TEXT NOT NULL, occurred_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL, source TEXT NOT NULL DEFAULT '', story_event_id TEXT NOT NULL DEFAULT '', idempotency_key TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', UNIQUE(lot_id, sequence_number), UNIQUE(lot_id, idempotency_key), FOREIGN KEY(lot_id) REFERENCES tracked_item_lots(lot_id))");
            stmt.execute("INSERT INTO schema_version VALUES (3, 'automatic tracking lot identity and lineage', 0)");

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO tracked_item_lots "
                            + "(lot_id, created_at, first_seen_at, last_seen_at, content_key, "
                            + "provider_id, material, fingerprint, initial_amount, current_amount, "
                            + "tracking_reason, created_by_subject) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, lotId);
                ps.setLong(2, 1000L);
                ps.setLong(3, 1000L);
                ps.setLong(4, 2000L);
                ps.setString(5, "minecraft:cobblestone");
                ps.setString(6, "minecraft");
                ps.setString(7, "minecraft:cobblestone");
                ps.setString(8, fingerprint);
                ps.setInt(9, amount);
                ps.setInt(10, amount);
                ps.setString(11, "AUTOMATIC");
                ps.setString(12, createdBySubject);
                ps.executeUpdate();
            }
        }

        return new DatabaseManager(dbPath);
    }

    private DatabaseManager migrateV3ToV4(DatabaseManager v3db) throws Exception {
        v3db.initialize();
        return v3db;
    }

    private void insertV3Lot(Path dbPath, String lotId, String createdBySubject,
                              String fingerprint, int amount, long lastSeenAt) throws Exception {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO tracked_item_lots "
                             + "(lot_id, created_at, first_seen_at, last_seen_at, content_key, "
                             + "provider_id, material, fingerprint, initial_amount, current_amount, "
                             + "tracking_reason, created_by_subject) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, lotId);
            ps.setLong(2, 1000L);
            ps.setLong(3, 1000L);
            ps.setLong(4, lastSeenAt);
            ps.setString(5, "minecraft:cobblestone");
            ps.setString(6, "minecraft");
            ps.setString(7, "minecraft:cobblestone");
            ps.setString(8, fingerprint);
            ps.setInt(9, amount);
            ps.setInt(10, amount);
            ps.setString(11, "AUTOMATIC");
            ps.setString(12, createdBySubject);
            ps.executeUpdate();
        }
    }

    private void insertV3LotLedger(Path dbPath, String entryId, String lotId,
                                    int seqNum, String subjectType, String subjectId,
                                    String displayName) throws Exception {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO item_lot_ownership_ledger "
                             + "(entry_id, lot_id, sequence_number, new_subject_type, new_subject_id, "
                             + "new_subject_display, transition_reason, occurred_at, recorded_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, entryId);
            ps.setString(2, lotId);
            ps.setInt(3, seqNum);
            ps.setString(4, subjectType);
            ps.setString(5, subjectId);
            ps.setString(6, displayName);
            ps.setString(7, "AUTOMATIC_TRACKING");
            ps.setLong(8, 1000L);
            ps.setLong(9, 1000L);
            ps.executeUpdate();
        }
    }

    private void insertV3Lineage(Path dbPath, String entryId, String lotId,
                                  String relatedLotId, String relationType,
                                  String idempotencyKey) throws Exception {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO lot_lineage "
                             + "(entry_id, lot_id, related_lot_id, relation_type, "
                             + "lot_amount, related_amount, occurred_at, source, idempotency_key) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, entryId);
            ps.setString(2, lotId);
            ps.setString(3, relatedLotId);
            ps.setString(4, relationType);
            ps.setInt(5, 32);
            ps.setInt(6, 16);
            ps.setLong(7, 1000L);
            ps.setString(8, "test");
            ps.setString(9, idempotencyKey);
            ps.executeUpdate();
        }
    }

    // === UUID parsing tests ===

    @Test
    void playerWithDisplayNameBackfillsUuidOnly() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":PlayerName";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-display.db", subject, fp, lotId, 64);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT owner_type, owner_stable_id, owner_display_snapshot, current_amount "
                             + "FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            assertEquals("player", rs.getString("owner_type"));
            assertEquals(playerUuid.toString(), rs.getString("owner_stable_id"),
                    "owner_stable_id must be UUID only, not UUID:display_name");
            assertEquals("PlayerName", rs.getString("owner_display_snapshot"));
            assertEquals(64, rs.getInt("current_amount"));
        }
    }

    @Test
    void displayNameNotIncludedInOwnerStableId() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":Bob";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-no-display-in-id.db", subject, fp, lotId, 32);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT owner_stable_id FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            String stableId = rs.getString("owner_stable_id");
            assertEquals(36, stableId.length(), "owner_stable_id must be exactly 36 chars (canonical UUID)");
            assertFalse(stableId.contains(":"), "owner_stable_id must not contain separators");
            assertFalse(stableId.contains("Bob"), "owner_stable_id must not contain display name");
        }
    }

    @Test
    void sameUuidDifferentDisplayNamesResolvesToOneAggregate() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId1 = UUID.randomUUID().toString();
        String lotId2 = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-same-uuid.db",
                "player:" + playerUuid + ":Name1", fp, lotId1, 32);

        insertV3Lot(tempDir.resolve("backfill-same-uuid.db"), lotId2,
                "player:" + playerUuid + ":Name2", fp, 48, 3000L);

        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM tracked_item_lots "
                             + "WHERE owner_type = 'player' AND owner_stable_id = '" + playerUuid + "' "
                             + "AND fingerprint = '" + fp + "'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Same UUID + different display names must resolve to one aggregate");
        }
    }

    @Test
    void displayNameWithSeparatorsDoesNotBreakStableIdParsing() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":Name:With:Colons";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-separators.db", subject, fp, lotId, 16);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT owner_stable_id, owner_display_snapshot FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            assertEquals(playerUuid.toString(), rs.getString("owner_stable_id"),
                    "UUID must be extracted correctly despite separators in display name");
            assertEquals("Name:With:Colons", rs.getString("owner_display_snapshot"));
        }
    }

    @Test
    void malformedLegacySubjectUsesDocumentedFallback() throws Exception {
        String subject = "system:my-token";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-malformed.db", subject, fp, lotId, 16);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT owner_type, owner_stable_id FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            assertEquals("", rs.getString("owner_type"), "Non-player subjects must not be backfilled");
            assertEquals("", rs.getString("owner_stable_id"), "Non-player subjects must have empty owner_stable_id");
        }
    }

    @Test
    void migratedOwnerStableIdIsCanonicalLowercaseUuid() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid.toString().toUpperCase() + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-lowercase.db", subject, fp, lotId, 10);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT owner_stable_id FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            String stableId = rs.getString("owner_stable_id");
            assertEquals(playerUuid.toString(), stableId,
                    "UUID must be extracted as-is from the subject (canonical form)");
            assertEquals(36, stableId.length(), "Canonical UUID is 36 characters");
        }
    }

    // === Data preservation tests ===

    @Test
    void existingAmountIsPreservedAfterBackfill() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-amount.db", subject, fp, lotId, 42);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT current_amount, initial_amount FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            assertEquals(42, rs.getInt("current_amount"));
            assertEquals(42, rs.getInt("initial_amount"));
        }
    }

    @Test
    void existingTimestampsArePreservedAfterBackfill() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-timestamps.db", subject, fp, lotId, 10);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT created_at, first_seen_at, last_seen_at FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            assertEquals(1000L, rs.getLong("created_at"));
            assertEquals(1000L, rs.getLong("first_seen_at"));
            assertEquals(2000L, rs.getLong("last_seen_at"));
        }
    }

    @Test
    void existingLotIdIsPreservedAfterBackfill() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("backfill-lotid.db", subject, fp, lotId, 10);
        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT lot_id FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs.next());
            assertEquals(lotId, rs.getString("lot_id"));
        }
    }

    // === Duplicate migration tests ===

    @Test
    void duplicateMigrationPreservesOwnershipLedger() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String canonicalLotId = UUID.randomUUID().toString();
        String dupLotId = UUID.randomUUID().toString();

        // Create v3 db with canonical lot (latest last_seen_at)
        DatabaseManager v3db = createV3Database("dup-ledger.db", subject, fp, canonicalLotId, 32);

        // Insert duplicate lot (older last_seen_at)
        insertV3Lot(tempDir.resolve("dup-ledger.db"), dupLotId, subject, fp, 16, 1000L);

        // Insert ownership ledger entries for both lots
        insertV3LotLedger(tempDir.resolve("dup-ledger.db"), UUID.randomUUID().toString(),
                canonicalLotId, 1, "player", playerUuid.toString(), "TestPlayer");
        insertV3LotLedger(tempDir.resolve("dup-ledger.db"), UUID.randomUUID().toString(),
                dupLotId, 1, "player", playerUuid.toString(), "TestPlayer");

        migrateV3ToV4(v3db);

        // All ledger entries should be preserved and reference the canonical lot
        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM item_lot_ownership_ledger WHERE lot_id = '" + canonicalLotId + "'")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "Both ledger entries should be reassigned to canonical lot");
        }

        // No entries should reference the deleted duplicate lot
        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM item_lot_ownership_ledger WHERE lot_id = '" + dupLotId + "'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "No ledger entries should reference deleted duplicate lot");
        }
    }

    @Test
    void duplicateMigrationPreservesLineageReferences() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String canonicalLotId = UUID.randomUUID().toString();
        String dupLotId = UUID.randomUUID().toString();
        String otherLotId = UUID.randomUUID().toString();

        // Create v3 db with canonical lot
        DatabaseManager v3db = createV3Database("dup-lineage.db", subject, fp, canonicalLotId, 32);

        // Insert duplicate lot (older last_seen_at)
        insertV3Lot(tempDir.resolve("dup-lineage.db"), dupLotId, subject, fp, 16, 1000L);

        // Insert a third lot for lineage reference
        insertV3Lot(tempDir.resolve("dup-lineage.db"), otherLotId,
                "player:" + UUID.randomUUID() + ":Other", "other-fp", 8, 500L);

        // Insert lineage: dup -> other (SPLIT_FROM)
        insertV3Lineage(tempDir.resolve("dup-lineage.db"), UUID.randomUUID().toString(),
                dupLotId, otherLotId, "SPLIT_FROM", "lineage-dup-1");

        // Insert lineage: other -> dup (MERGED_INTO)
        insertV3Lineage(tempDir.resolve("dup-lineage.db"), UUID.randomUUID().toString(),
                otherLotId, dupLotId, "MERGED_INTO", "lineage-dup-2");

        migrateV3ToV4(v3db);

        // Lineage entries should be reassigned to canonical lot
        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM lot_lineage WHERE lot_id = '" + canonicalLotId + "'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Lineage with dup as lot_id should be reassigned to canonical");
        }

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM lot_lineage WHERE related_lot_id = '" + canonicalLotId + "'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Lineage with dup as related_lot_id should be reassigned to canonical");
        }

        // No lineage should reference the deleted duplicate
        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM lot_lineage WHERE lot_id = '" + dupLotId + "' OR related_lot_id = '" + dupLotId + "'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "No lineage should reference deleted duplicate lot");
        }
    }

    @Test
    void duplicateMigrationLeavesExactlyOneAggregateRow() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";

        DatabaseManager v3db = createV3Database("dup-one-row.db", subject, fp,
                UUID.randomUUID().toString(), 10);

        // Insert 2 more duplicates
        insertV3Lot(tempDir.resolve("dup-one-row.db"), UUID.randomUUID().toString(),
                subject, fp, 20, 3000L);
        insertV3Lot(tempDir.resolve("dup-one-row.db"), UUID.randomUUID().toString(),
                subject, fp, 30, 1000L);

        migrateV3ToV4(v3db);

        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM tracked_item_lots "
                             + "WHERE owner_type = 'player' AND owner_stable_id = '" + playerUuid + "' "
                             + "AND fingerprint = '" + fp + "'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Exactly one aggregate row should remain");
        }
    }

    @Test
    void uniqueIndexCreationSucceedsAfterCanonicalization() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";

        DatabaseManager v3db = createV3Database("dup-unique.db", subject, fp,
                UUID.randomUUID().toString(), 10);
        insertV3Lot(tempDir.resolve("dup-unique.db"), UUID.randomUUID().toString(),
                subject, fp, 20, 3000L);

        // Migration should succeed (duplicates resolved before UNIQUE index creation)
        migrateV3ToV4(v3db);

        // Verify UNIQUE index exists
        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_tracked_lots_owner_fp_unique'")) {
            assertTrue(rs.next(), "UNIQUE index should exist after migration");
        }
    }

    @Test
    void migrationRollbackLeavesOriginalV3DataIntact() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("rollback.db", subject, fp, lotId, 32);

        // Record original state
        int originalCount;
        try (Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + tempDir.resolve("rollback.db"));
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tracked_item_lots")) {
            rs.next();
            originalCount = rs.getInt(1);
        }

        // Simulate migration failure: manually add owner_type column so ALTER TABLE fails
        try (Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + tempDir.resolve("rollback.db"));
             Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE tracked_item_lots ADD COLUMN owner_type TEXT NOT NULL DEFAULT ''");
        }

        // Now try to migrate — ALTER TABLE should fail (duplicate column)
        try {
            v3db.initialize();
        } catch (Exception expected) {
            // Expected: migration fails
        }

        // Verify original v3 data is intact (the manually added column doesn't affect data)
        try (Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + tempDir.resolve("rollback.db"));
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tracked_item_lots")) {
            rs.next();
            assertEquals(originalCount, rs.getInt(1), "Original v3 data must be intact after rollback");
        }

        // Verify v4 was NOT recorded
        try (Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + tempDir.resolve("rollback.db"));
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM schema_version WHERE version = 4")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "v4 should not be recorded after failed migration");
        }
    }

    @Test
    void migrationRerunIsIdempotent() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String subject = "player:" + playerUuid + ":TestPlayer";
        String fp = "minecraft|cobblestone|minecraft:cobblestone|0||||||||0||";
        String lotId = UUID.randomUUID().toString();

        DatabaseManager v3db = createV3Database("idempotent.db", subject, fp, lotId, 64);
        migrateV3ToV4(v3db);

        // Verify state after first migration
        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs1 = stmt.executeQuery(
                     "SELECT owner_type, owner_stable_id, owner_display_snapshot, current_amount "
                             + "FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs1.next());
            assertEquals("player", rs1.getString("owner_type"));
            assertEquals(playerUuid.toString(), rs1.getString("owner_stable_id"));
            assertEquals("TestPlayer", rs1.getString("owner_display_snapshot"));
            assertEquals(64, rs1.getInt("current_amount"));
        }

        // Re-run migration (should be no-op)
        v3db.initialize();

        // State should be unchanged
        try (Connection connection = v3db.openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs2 = stmt.executeQuery(
                     "SELECT owner_type, owner_stable_id, owner_display_snapshot, current_amount "
                             + "FROM tracked_item_lots WHERE lot_id = '" + lotId + "'")) {
            assertTrue(rs2.next());
            assertEquals("player", rs2.getString("owner_type"));
            assertEquals(playerUuid.toString(), rs2.getString("owner_stable_id"));
            assertEquals("TestPlayer", rs2.getString("owner_display_snapshot"));
            assertEquals(64, rs2.getInt("current_amount"));
        }
    }
}
