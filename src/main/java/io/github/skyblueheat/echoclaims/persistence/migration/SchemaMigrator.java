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
            ))
    );

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
