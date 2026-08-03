package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.audit.AuditRecord;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditWriteQueueTest {

    @Test
    void drainsEverySubmittedRecordOnShutdown() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, this::rethrow, 128, 8);
        queue.start();

        for (int index = 0; index < 50; index++) {
            assertTrue(queue.submit(record()));
        }

        assertTrue(queue.shutdown(Duration.ofSeconds(5)));
        assertEquals(50, repository.stored.size());
        assertEquals(50, queue.status().written());
        assertEquals(0, queue.status().dropped());
    }

    @Test
    void dropsInsteadOfBlockingWhenTheQueueIsFull() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, this::rethrow, 16, 1);

        int accepted = 0;
        for (int index = 0; index < 40; index++) {
            if (queue.submit(record())) {
                accepted++;
            }
        }

        assertEquals(16, accepted);
        assertEquals(24, queue.status().dropped());
        assertEquals(16, queue.status().pending());
    }

    @Test
    void writeFailuresAreReportedAndCounted() {
        FailingRepository repository = new FailingRepository();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        AuditWriteQueue queue = new AuditWriteQueue(repository, errors::add, 32, 1);
        queue.start();

        queue.submit(record());
        queue.shutdown(Duration.ofSeconds(5));

        assertEquals(1, errors.size());
        assertEquals(1, queue.status().failed());
        assertEquals(0, queue.status().written());
    }

    @Test
    void submissionsAfterShutdownAreRejected() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, this::rethrow, 32, 4);
        queue.start();
        queue.shutdown(Duration.ofSeconds(5));

        assertFalse(queue.submit(record()));
        assertTrue(repository.stored.isEmpty());
    }

    private void rethrow(Throwable throwable) {
        throw new AssertionError("unexpected write failure", throwable);
    }

    private static AuditRecord record() {
        return AuditRecord.now("test", "unit-test", "test record");
    }

    private static class RecordingRepository implements AuditRecordRepository {

        private final List<AuditRecord> stored = new CopyOnWriteArrayList<>();

        @Override
        public void insert(AuditRecord record) {
            stored.add(record);
        }

        @Override
        public int insertAll(Collection<AuditRecord> records) throws SQLException {
            stored.addAll(records);
            return records.size();
        }

        @Override
        public long count() {
            return stored.size();
        }
    }

    private static final class FailingRepository extends RecordingRepository {

        @Override
        public int insertAll(Collection<AuditRecord> records) throws SQLException {
            throw new SQLException("disk is on fire");
        }
    }
}
