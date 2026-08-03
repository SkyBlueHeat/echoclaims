package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.audit.AuditRecord;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteAuditRecordRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void insertAndCountWorks() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("audit_test.db"));
        database.initialize();

        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        assertEquals(0, repository.count());

        AuditRecord record = AuditRecord.now("test-category", "unit-test", "test details");
        repository.insert(record);

        assertEquals(1, repository.count());
    }

    @Test
    void insertAllInBatchWorks() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("batch_test.db"));
        database.initialize();

        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        List<AuditRecord> records = List.of(
                AuditRecord.now("cat1", "test", "r1"),
                AuditRecord.now("cat2", "test", "r2"),
                AuditRecord.now("cat1", "test", "r3")
        );

        int inserted = repository.insertAll(records);
        assertEquals(3, inserted);
        assertEquals(3, repository.count());
    }

    @Test
    void emptyBatchInsertReturnsZero() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("empty_batch.db"));
        database.initialize();

        SqliteAuditRecordRepository repository = new SqliteAuditRecordRepository(database);

        assertEquals(0, repository.insertAll(List.of()));
    }
}
