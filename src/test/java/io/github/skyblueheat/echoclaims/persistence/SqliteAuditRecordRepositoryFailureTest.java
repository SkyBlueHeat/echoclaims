package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.audit.AuditRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteAuditRecordRepositoryFailureTest {

    @TempDir
    Path tempDir;

    @Test
    void insertFailsWhenDatabaseNotInitialized() {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("uninitialized.db"));
        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        assertThrows(SQLException.class, () -> repository.insert(AuditRecord.now("test", "src", "det")));
    }

    @Test
    void insertAllFailsWhenDatabaseNotInitialized() {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("uninitialized2.db"));
        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        assertThrows(SQLException.class, () ->
                repository.insertAll(List.of(AuditRecord.now("test", "src", "det"))));
    }

    @Test
    void countFailsWhenDatabaseNotInitialized() {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("uninitialized3.db"));
        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        assertThrows(SQLException.class, repository::count);
    }

    @Test
    void duplicateInsertThrowsPrimaryKeyViolation() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("dup.db"));
        database.initialize();
        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        AuditRecord record = AuditRecord.now("test", "src", "det");
        repository.insert(record);

        assertThrows(SQLException.class, () -> repository.insert(record));
        assertEquals(1, repository.count());
    }

    @Test
    void batchInsertRollsBackOnFailure() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("batch_rollback.db"));
        database.initialize();
        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        AuditRecord first = AuditRecord.now("test", "src", "first");
        repository.insert(first);

        AuditRecord duplicate = new AuditRecord(
                first.id(), "test", "src", "duplicate", System.currentTimeMillis());
        AuditRecord unique = AuditRecord.now("test", "src", "unique");

        assertThrows(SQLException.class, () ->
                repository.insertAll(List.of(duplicate, unique)));

        assertEquals(1, repository.count(), "batch should have rolled back, leaving only the pre-inserted record");
    }

    @Test
    void insertNullRecordThrowsNPE() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("null.db"));
        database.initialize();
        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        assertThrows(NullPointerException.class, () -> repository.insert(null));
    }

    @Test
    void insertAllNullCollectionThrowsNPE() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("nullcoll.db"));
        database.initialize();
        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        assertThrows(NullPointerException.class, () -> repository.insertAll(null));
    }
}
