package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.audit.AuditRecord;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditWriteQueueConcurrencyTest {

    @Test
    void submitBeforeStartQueuesRecords() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 128, 8);

        assertTrue(queue.submit(record()));
        assertEquals(1, queue.status().submitted());
        assertEquals(1, queue.status().pending());

        queue.start();
        assertTrue(queue.shutdown(Duration.ofSeconds(5)));
        assertEquals(1, repository.stored.size());
    }

    @Test
    void doubleStartThrows() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 32, 4);
        queue.start();

        assertThrows(IllegalStateException.class, queue::start);

        queue.shutdown(Duration.ofSeconds(5));
    }

    @Test
    void shutdownBeforeStartReturnsImmediately() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 32, 4);

        long start = System.nanoTime();
        boolean result = queue.shutdown(Duration.ofSeconds(5));
        long elapsed = System.nanoTime() - start;

        assertTrue(result);
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1), "shutdown before start should not block");
    }

    @Test
    void doubleShutdownIsSafe() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 32, 4);
        queue.start();
        queue.submit(record());

        assertTrue(queue.shutdown(Duration.ofSeconds(5)));
        boolean second = queue.shutdown(Duration.ofSeconds(5));
        assertTrue(second);
        assertEquals(1, repository.stored.size());
    }

    @Test
    void simultaneousSubmitAndShutdown() throws InterruptedException {
        Phaser phaser = new Phaser(2);
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 256, 1);
        queue.start();

        CountDownLatch submitDone = new CountDownLatch(1);

        Thread submitter = new Thread(() -> {
            phaser.arriveAndAwaitAdvance();
            for (int i = 0; i < 100; i++) {
                queue.submit(record());
            }
            submitDone.countDown();
        });

        submitter.start();
        phaser.arriveAndAwaitAdvance();

        queue.shutdown(Duration.ofSeconds(10));
        submitDone.await(5, TimeUnit.SECONDS);

        AuditWriteQueue.QueueStatus status = queue.status();
        assertEquals(0, status.pending(), "no records should remain pending");
        assertTrue(status.written() + status.dropped() > 0,
                "some records should have been written or dropped");
        assertTrue(status.written() <= status.submitted(),
                "written cannot exceed submitted");
    }

    @Test
    void shutdownDuringActiveBatchWrite() throws InterruptedException {
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch writeProceed = new CountDownLatch(1);
        ControlledRepository repository = new ControlledRepository(writeStarted, writeProceed);
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 128, 64);
        queue.start();

        for (int i = 0; i < 5; i++) {
            queue.submit(record());
        }

        writeStarted.await(5, TimeUnit.SECONDS);

        Thread shutdownThread = new Thread(() -> queue.shutdown(Duration.ofSeconds(10)));
        shutdownThread.start();

        writeProceed.countDown();
        shutdownThread.join(15_000);
        assertFalse(shutdownThread.isAlive(), "shutdown thread should have completed");

        AuditWriteQueue.QueueStatus status = queue.status();
        assertEquals(5, status.written() + status.failed() + status.dropped());
        assertEquals(0, status.pending());
    }

    @Test
    void partialRepositoryFailureCountsCorrectly() {
        FailOnceRepository repository = new FailOnceRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 128, 1);
        queue.start();

        queue.submit(record());
        queue.submit(record());

        queue.shutdown(Duration.ofSeconds(5));

        AuditWriteQueue.QueueStatus status = queue.status();
        assertEquals(2, status.submitted());
        assertEquals(1, status.failed());
        assertEquals(1, status.written());
    }

    @Test
    void shutdownTimeoutReturnsFalse() throws InterruptedException {
        BlockingRepository repository = new BlockingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 128, 1);
        queue.start();

        queue.submit(record());

        boolean drained = queue.shutdown(Duration.ofMillis(50));
        assertFalse(drained, "shutdown should time out with blocking repository");

        repository.unblock();
    }

    @Test
    void workerThreadTerminatesAfterShutdown() throws InterruptedException {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 32, 4);
        queue.start();
        assertTrue(queue.shutdown(Duration.ofSeconds(5)));

        boolean writerAlive = false;
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread t : threads) {
            if ("echoclaims-sqlite-writer".equals(t.getName())) {
                writerAlive = t.isAlive();
            }
        }
        assertFalse(writerAlive, "writer thread should have terminated after shutdown");
    }

    @Test
    void metricsAreConsistentAfterFullCycle() {
        RecordingRepository repository = new RecordingRepository();
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 16, 4);
        queue.start();

        for (int i = 0; i < 10; i++) {
            queue.submit(record());
        }

        queue.shutdown(Duration.ofSeconds(5));

        AuditWriteQueue.QueueStatus status = queue.status();
        assertEquals(10, status.submitted());
        assertEquals(10, status.written());
        assertEquals(0, status.failed());
        assertEquals(0, status.dropped());
        assertEquals(0, status.pending());
    }

    private static AuditRecord record() {
        return AuditRecord.now("test", "unit-test", "test record");
    }

    private static class RecordingRepository implements AuditRecordRepository {
        final List<AuditRecord> stored = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void insert(AuditRecord record) {
            stored.add(record);
        }

        @Override
        public int insertAll(Collection<AuditRecord> records) {
            stored.addAll(records);
            return records.size();
        }

        @Override
        public long count() {
            return stored.size();
        }
    }

    private static final class ControlledRepository extends RecordingRepository {
        private final CountDownLatch writeStarted;
        private final CountDownLatch writeProceed;

        ControlledRepository(CountDownLatch writeStarted, CountDownLatch writeProceed) {
            this.writeStarted = writeStarted;
            this.writeProceed = writeProceed;
        }

        @Override
        public int insertAll(Collection<AuditRecord> records) {
            writeStarted.countDown();
            try {
                writeProceed.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.insertAll(records);
        }
    }

    private static final class FailOnceRepository extends RecordingRepository {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public int insertAll(Collection<AuditRecord> records) {
            if (calls.getAndIncrement() == 0) {
                throw new RuntimeException("first write fails");
            }
            return super.insertAll(records);
        }
    }

    private static final class BlockingRepository extends RecordingRepository {
        private volatile boolean unblocked = false;

        @Override
        public int insertAll(Collection<AuditRecord> records) {
            while (!unblocked) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.insertAll(records);
        }

        void unblock() {
            unblocked = true;
        }
    }
}
