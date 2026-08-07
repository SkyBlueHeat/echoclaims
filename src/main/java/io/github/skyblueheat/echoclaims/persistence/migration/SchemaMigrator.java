package io.github.skyblueheat.echoclaims.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Applies versioned migrations inside a transaction and records what was applied.
 *
 * <p>Migrations only ever move forward and never drop recorded history, which keeps the
 * "never silently delete audit history" rule enforceable at the schema level.</p>
 */
public final class SchemaMigrator {

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "echoclaims audit record foundation", List.of(
                    """
                    CREATE TABLE IF NOT EXISTS audit_records (
                        id TEXT PRIMARY KEY,
                        category TEXT NOT NULL,
                        source TEXT NOT NULL DEFAULT '',
                        details TEXT NOT NULL DEFAULT '',
                        recorded_at INTEGER NOT NULL
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_audit_records_recorded_at
                    ON audit_records(recorded_at DESC)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_audit_records_category
                    ON audit_records(category)
                    """
            )),
            new Migration(2, "evidence foundation: inventory snapshots, snapshot items, incidents", List.of(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_snapshots (
                        id TEXT PRIMARY KEY,
                        player_uuid TEXT NOT NULL,
                        capture_reason TEXT NOT NULL,
                        captured_at INTEGER NOT NULL,
                        world_id TEXT NOT NULL,
                        x INTEGER,
                        y INTEGER,
                        z INTEGER,
                        game_mode TEXT NOT NULL DEFAULT '',
                        health REAL NOT NULL DEFAULT 0,
                        food_level INTEGER NOT NULL DEFAULT 0,
                        experience_level INTEGER NOT NULL DEFAULT 0,
                        total_experience INTEGER NOT NULL DEFAULT 0,
                        schema_version INTEGER NOT NULL DEFAULT 1
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_player_uuid
                    ON inventory_snapshots(player_uuid)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_inventory_snapshots_captured_at
                    ON inventory_snapshots(captured_at DESC)
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS snapshot_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        snapshot_uuid TEXT NOT NULL,
                        slot TEXT NOT NULL,
                        slot_type TEXT NOT NULL,
                        material_key TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        serialized_data TEXT NOT NULL DEFAULT '',
                        content_identity TEXT NOT NULL DEFAULT '',
                        score INTEGER NOT NULL DEFAULT 0,
                        display_name TEXT NOT NULL DEFAULT '',
                        damage INTEGER NOT NULL DEFAULT 0,
                        max_durability INTEGER NOT NULL DEFAULT 0,
                        enchantments TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (snapshot_uuid) REFERENCES inventory_snapshots(id) ON DELETE CASCADE,
                        UNIQUE(snapshot_uuid, slot)
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_snapshot_items_snapshot_uuid
                    ON snapshot_items(snapshot_uuid)
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS incidents (
                        id TEXT PRIMARY KEY,
                        incident_type TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        occurred_at INTEGER NOT NULL,
                        world_id TEXT NOT NULL,
                        x INTEGER,
                        y INTEGER,
                        z INTEGER,
                        cause TEXT NOT NULL DEFAULT '',
                        killer_player_uuid TEXT,
                        killer_entity_key TEXT NOT NULL DEFAULT '',
                        pre_event_snapshot_uuid TEXT NOT NULL,
                        post_event_snapshot_uuid TEXT,
                        status TEXT NOT NULL DEFAULT 'OPEN',
                        metadata TEXT NOT NULL DEFAULT '',
                        deduplication_key TEXT NOT NULL UNIQUE,
                        FOREIGN KEY (pre_event_snapshot_uuid) REFERENCES inventory_snapshots(id) ON DELETE RESTRICT,
                        FOREIGN KEY (post_event_snapshot_uuid) REFERENCES inventory_snapshots(id) ON DELETE SET NULL
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_incidents_player_uuid
                    ON incidents(player_uuid)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_incidents_occurred_at
                    ON incidents(occurred_at DESC)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_incidents_incident_type
                    ON incidents(incident_type)
                    """
            )),
            new Migration(3, "claim foundation: claims and claim audit entries", List.of(
                    """
                    CREATE TABLE IF NOT EXISTS claims (
                        id TEXT PRIMARY KEY,
                        public_reference TEXT NOT NULL UNIQUE,
                        incident_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'DRAFT',
                        source TEXT NOT NULL DEFAULT 'PLAYER_COMMAND',
                        description TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        submitted_at INTEGER NOT NULL DEFAULT 0,
                        cancelled_at INTEGER NOT NULL DEFAULT 0,
                        version INTEGER NOT NULL DEFAULT 0,
                        metadata TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE RESTRICT
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claims_player_uuid
                    ON claims(player_uuid)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claims_status
                    ON claims(status)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claims_incident_id
                    ON claims(incident_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claims_created_at
                    ON claims(created_at DESC)
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS claim_audit_entries (
                        id TEXT PRIMARY KEY,
                        claim_id TEXT NOT NULL,
                        actor_type TEXT NOT NULL,
                        actor_uuid TEXT,
                        action TEXT NOT NULL,
                        reason TEXT NOT NULL DEFAULT '',
                        recorded_at INTEGER NOT NULL,
                        claim_version INTEGER NOT NULL,
                        metadata TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claim_audit_claim_id
                    ON claim_audit_entries(claim_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claim_audit_recorded_at
                    ON claim_audit_entries(recorded_at DESC)
                    """
            )),
            new Migration(4, "claim duplicate active protection: partial unique index", List.of(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_claims_active_unique
                    ON claims(incident_id, player_uuid)
                    WHERE status IN ('DRAFT', 'SUBMITTED')
                    """
            )),
            new Migration(5, "review foundation: claim reviews, comments, and item decisions", List.of(
                    """
                    CREATE TABLE IF NOT EXISTS claim_reviews (
                        id TEXT PRIMARY KEY,
                        claim_id TEXT NOT NULL,
                        assigned_reviewer_uuid TEXT NOT NULL,
                        review_state TEXT NOT NULL DEFAULT 'OPEN',
                        final_outcome TEXT,
                        final_summary TEXT NOT NULL DEFAULT '',
                        started_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        finalized_at INTEGER NOT NULL DEFAULT 0,
                        version INTEGER NOT NULL DEFAULT 0,
                        metadata TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT,
                        UNIQUE(claim_id)
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claim_reviews_claim_id
                    ON claim_reviews(claim_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claim_reviews_reviewer
                    ON claim_reviews(assigned_reviewer_uuid)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claim_reviews_state
                    ON claim_reviews(review_state)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_claim_reviews_finalized_at
                    ON claim_reviews(finalized_at DESC)
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS claim_review_comments (
                        id TEXT PRIMARY KEY,
                        review_id TEXT NOT NULL,
                        claim_id TEXT NOT NULL,
                        actor_uuid TEXT NOT NULL,
                        actor_type TEXT NOT NULL,
                        visibility TEXT NOT NULL,
                        comment_type TEXT NOT NULL,
                        body TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        metadata TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (review_id) REFERENCES claim_reviews(id) ON DELETE RESTRICT,
                        FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_review_comments_claim_id
                    ON claim_review_comments(claim_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_review_comments_review_id
                    ON claim_review_comments(review_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_review_comments_claim_created
                    ON claim_review_comments(claim_id, created_at ASC)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_review_comments_visibility
                    ON claim_review_comments(visibility)
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS claim_review_item_decisions (
                        id TEXT PRIMARY KEY,
                        review_id TEXT NOT NULL,
                        claim_id TEXT NOT NULL,
                        evidence_item_reference TEXT NOT NULL,
                        source_snapshot_id TEXT NOT NULL,
                        original_quantity INTEGER NOT NULL,
                        approved_quantity INTEGER NOT NULL,
                        outcome TEXT NOT NULL,
                        reason_code TEXT NOT NULL,
                        staff_note TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        version INTEGER NOT NULL DEFAULT 0,
                        metadata TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (review_id) REFERENCES claim_reviews(id) ON DELETE RESTRICT,
                        FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT,
                        UNIQUE(review_id, evidence_item_reference)
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_review_decisions_review_id
                    ON claim_review_item_decisions(review_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_review_decisions_claim_id
                    ON claim_review_item_decisions(claim_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_review_decisions_evidence_ref
                    ON claim_review_item_decisions(evidence_item_reference)
                    """
            )),
            new Migration(6, "refund foundation: refunds, refund items, and refund audit entries", List.of(
                    """
                    CREATE TABLE IF NOT EXISTS refunds (
                        id TEXT PRIMARY KEY,
                        claim_id TEXT NOT NULL,
                        review_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        created_at INTEGER NOT NULL,
                        completed_at INTEGER NOT NULL DEFAULT 0,
                        version INTEGER NOT NULL DEFAULT 0,
                        metadata TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT,
                        FOREIGN KEY (review_id) REFERENCES claim_reviews(id) ON DELETE RESTRICT,
                        UNIQUE(review_id)
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refunds_claim_id
                    ON refunds(claim_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refunds_player_uuid
                    ON refunds(player_uuid)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refunds_status
                    ON refunds(status)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refunds_player_status
                    ON refunds(player_uuid, status)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refunds_created_at
                    ON refunds(created_at DESC)
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS refund_items (
                        id TEXT PRIMARY KEY,
                        refund_id TEXT NOT NULL,
                        claim_id TEXT NOT NULL,
                        review_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        evidence_item_reference TEXT NOT NULL,
                        source_snapshot_id TEXT NOT NULL,
                        material_key TEXT NOT NULL,
                        refundable_quantity INTEGER NOT NULL,
                        delivered_quantity INTEGER NOT NULL DEFAULT 0,
                        serialized_item_data TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        failure_reason TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        version INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (refund_id) REFERENCES refunds(id) ON DELETE RESTRICT,
                        FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT,
                        FOREIGN KEY (review_id) REFERENCES claim_reviews(id) ON DELETE RESTRICT,
                        UNIQUE(refund_id, evidence_item_reference)
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_items_refund_id
                    ON refund_items(refund_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_items_claim_id
                    ON refund_items(claim_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_items_player_uuid
                    ON refund_items(player_uuid)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_items_status
                    ON refund_items(status)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_items_evidence_ref
                    ON refund_items(evidence_item_reference)
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS refund_audit_entries (
                        id TEXT PRIMARY KEY,
                        refund_id TEXT NOT NULL,
                        claim_id TEXT NOT NULL,
                        review_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        actor_uuid TEXT,
                        action TEXT NOT NULL,
                        refund_item_id TEXT,
                        quantity INTEGER NOT NULL DEFAULT 0,
                        reason TEXT NOT NULL DEFAULT '',
                        recorded_at INTEGER NOT NULL,
                        metadata TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (refund_id) REFERENCES refunds(id) ON DELETE RESTRICT,
                        FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT,
                        FOREIGN KEY (review_id) REFERENCES claim_reviews(id) ON DELETE RESTRICT
                    )
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_audit_refund_id
                    ON refund_audit_entries(refund_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_audit_claim_id
                    ON refund_audit_entries(claim_id)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_audit_player_uuid
                    ON refund_audit_entries(player_uuid)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_audit_recorded_at
                    ON refund_audit_entries(recorded_at DESC)
                    """,
                    """
                    CREATE INDEX IF NOT EXISTS idx_refund_audit_action
                    ON refund_audit_entries(action)
                    """
            ))
    );

    static {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Migration migration : MIGRATIONS) {
            if (!seen.add(migration.version())) {
                throw new IllegalStateException(
                        "Duplicate migration version: " + migration.version());
            }
        }
    }

    private SchemaMigrator() {
    }

    public static List<Migration> migrations() {
        return MIGRATIONS;
    }

    public static int latestVersion() {
        return MIGRATIONS.stream().mapToInt(Migration::version).max().orElse(0);
    }

    /**
     * Brings the database up to {@link #latestVersion()}.
     *
     * @return the number of migrations that were applied by this call
     */
    public static int migrate(Connection connection) throws SQLException {
        createVersionTable(connection);
        int current = currentVersion(connection);
        int applied = 0;

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Migration migration : MIGRATIONS) {
                if (migration.version() <= current) {
                    continue;
                }
                apply(connection, migration);
                applied++;
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }

        return applied;
    }

    public static int currentVersion(Connection connection) throws SQLException {
        createVersionTable(connection);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static void apply(Connection connection, Migration migration) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : migration.statements()) {
                statement.execute(sql);
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_version(version, description, applied_at) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static void createVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at INTEGER NOT NULL
                    )
                    """);
        }
    }
}
