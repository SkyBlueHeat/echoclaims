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
