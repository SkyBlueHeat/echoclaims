package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.audit.AuditRecord;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bounded single-writer persistence path.
 *
 * <p>{@link #submit(AuditRecord)} is called from the server thread and only performs
 * a non-blocking hand-off, so a slow disk can never stall a tick. One dedicated thread
 * drains the queue in batches; when the queue is full the event is dropped and counted
 * instead of applying back pressure to the server.</p>
 */
public final class AuditWriteQueue implements AutoCloseable {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final AuditRecordRepository repository;
    private final Consumer<Throwable> errorHandler;
    private final BlockingQueue<AuditRecord> queue;
    private final int batchSize;
    private final Thread writer;
    private final CountDownLatch stopped = new CountDownLatch(1);

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    private volatile boolean running = true;

    public AuditWriteQueue(
            AuditRecordRepository repository,
            Consumer<Throwable> errorHandler,
            int capacity,
            int batchSize
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.queue = new ArrayBlockingQueue<>(Math.max(16, capacity));
        this.batchSize = Math.max(1, batchSize);
        this.writer = Thread.ofPlatform()
                .name("echoclaims-sqlite-writer")
                .daemon(true)
                .unstarted(this::runWriteLoop);
    }

    public void start() {
        writer.start();
    }

    /**
     * @return {@code false} when the queue was full and the event had to be dropped
     */
    public boolean submit(AuditRecord record) {
        Objects.requireNonNull(record, "record");
        if (!running) {
            dropped.incrementAndGet();
            return false;
        }

        submitted.incrementAndGet();
        if (queue.offer(record)) {
            return true;
        }

        dropped.incrementAndGet();
        return false;
    }

    public QueueStatus status() {
        return new QueueStatus(
                submitted.get(),
                written.get(),
                failed.get(),
                dropped.get(),
                queue.size()
        );
    }

    /**
     * Stops accepting work, drains what is queued, and joins the writer thread.
     *
     * @return {@code true} when everything queued was flushed before the timeout
     */
    public boolean shutdown(Duration timeout) {
        running = false;
        writer.interrupt();

        try {
            boolean finished = stopped.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return finished && queue.isEmpty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        shutdown(Duration.ofSeconds(10));
    }

    private void runWriteLoop() {
        try {
            while (running) {
                AuditRecord head = queue.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
                if (head != null) {
                    writeBatch(head);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            drainRemaining();
            stopped.countDown();
        }
    }

    private void drainRemaining() {
        AuditRecord head;
        while ((head = queue.poll()) != null) {
            writeBatch(head);
        }
    }

    private void writeBatch(AuditRecord head) {
        List<AuditRecord> batch = new ArrayList<>(batchSize);
        batch.add(head);
        queue.drainTo(batch, batchSize - 1);

        try {
            repository.insertAll(batch);
            written.addAndGet(batch.size());
        } catch (SQLException | RuntimeException exception) {
            failed.addAndGet(batch.size());
            errorHandler.accept(exception);
        }
    }

    public record QueueStatus(
            long submitted,
            long written,
            long failed,
            long dropped,
            int pending
    ) {
    }
}
