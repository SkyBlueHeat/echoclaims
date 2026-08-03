package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.audit.AuditRecord;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Objects;

/**
 * SQLite implementation of {@link AuditRecordRepository}.
 *
 * <p>Every method blocks on disk I/O and must therefore be called from the EchoClaims
 * writer thread, never from the server thread.</p>
 */
public final class SqliteAuditRecordRepository implements AuditRecordRepository {

    private final DatabaseManager databaseManager;

    public SqliteAuditRecordRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public void insert(AuditRecord record) throws SQLException {
        Objects.requireNonNull(record, "record");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO audit_records(id, category, source, details, recorded_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, record.id().toString());
            statement.setString(2, record.category());
            statement.setString(3, record.source());
            statement.setString(4, record.details());
            statement.setLong(5, record.recordedAt());
            statement.executeUpdate();
        }
    }

    @Override
    public int insertAll(Collection<AuditRecord> records) throws SQLException {
        Objects.requireNonNull(records, "records");
        if (records.isEmpty()) {
            return 0;
        }

        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO audit_records(id, category, source, details, recorded_at) VALUES (?, ?, ?, ?, ?)")) {
                for (AuditRecord record : records) {
                    statement.setString(1, record.id().toString());
                    statement.setString(2, record.category());
                    statement.setString(3, record.source());
                    statement.setString(4, record.details());
                    statement.setLong(5, record.recordedAt());
                    statement.addBatch();
                }
                int[] counts = statement.executeBatch();
                connection.commit();
                int total = 0;
                for (int count : counts) {
                    total += Math.max(0, count);
                }
                return total;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM audit_records")) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }
}
