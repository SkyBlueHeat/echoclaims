package dev.worldecho.persistence;

import dev.worldecho.persistence.migration.SchemaMigrator;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite file, its connection settings, and its schema version.
 *
 * <p>Every method here blocks on disk I/O and must therefore be called from a WorldEcho
 * worker thread, never from the server thread.</p>
 */
public final class DatabaseManager {

    private final Path databasePath;
    private final SQLiteDataSource dataSource;

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath();

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5_000);

        this.dataSource = new SQLiteDataSource(config);
        this.dataSource.setUrl("jdbc:sqlite:" + this.databasePath);
    }

    /**
     * Creates the database file if needed and applies pending migrations.
     *
     * @return the number of migrations applied by this call
     */
    public int initialize() throws SQLException, IOException {
        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Connection connection = openConnection()) {
            return SchemaMigrator.migrate(connection);
        }
    }

    public Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public int schemaVersion() throws SQLException {
        try (Connection connection = openConnection()) {
            return SchemaMigrator.currentVersion(connection);
        }
    }

    /**
     * Cheap liveness probe used by {@code /worldecho status}.
     */
    public boolean healthy() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    public Path databasePath() {
        return databasePath;
    }

    public String jdbcUrl() {
        return "jdbc:sqlite:" + databasePath;
    }
}
