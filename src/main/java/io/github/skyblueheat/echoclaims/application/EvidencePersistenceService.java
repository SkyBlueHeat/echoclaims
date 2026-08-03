package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.persistence.EvidenceStore;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous persistence path for evidence captures.
 *
 * <p>Capture tasks are submitted on the server thread and executed on a bounded
 * single-thread executor. Each death evidence unit — inventory snapshot, all snapshot
 * items, and the incident — is persisted atomically in a single database transaction
 * via {@link EvidenceStore}. Either all three persist or none persist, guaranteeing
 * no orphan snapshots or incidents.</p>
 *
 * <p>Rejected captures (when the queue is full or the service is shutting down) are
 * counted but never silently discarded. Duplicate incidents (caught by the database
 * uniqueness constraint) are counted separately from failures.</p>
 */
public final class EvidencePersistenceService {

    private final EvidenceStore evidenceStore;
    private final EvidenceMetrics metrics;
    private final Consumer<Throwable> errorHandler;
    private final ThreadPoolExecutor executor;
    private final Logger logger;

    private volatile boolean running = true;

    public EvidencePersistenceService(
            EvidenceStore evidenceStore,
            EvidenceMetrics metrics,
            Consumer<Throwable> errorHandler,
            int queueCapacity,
            Logger logger
    ) {
        this.evidenceStore = Objects.requireNonNull(evidenceStore, "evidenceStore");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.logger = Objects.requireNonNull(logger, "logger");

        int capacity = Math.max(16, queueCapacity);
        this.executor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                r -> {
                    Thread thread = new Thread(r, "echoclaims-evidence-writer");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * Submits a death capture for asynchronous atomic persistence.
     *
     * <p>The snapshot, all items, and the incident are persisted in a single
     * transaction. Either all persist or none persist.</p>
     *
     * @return {@code true} if the capture was accepted, {@code false} if rejected
     */
    public boolean submitDeathCapture(InventorySnapshot snapshot, Incident incident) {
        if (!running) {
            metrics.recordRejectedCapture();
            return false;
        }

        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(incident, "incident");

        try {
            metrics.recordAcceptedCapture();
            executor.submit(() -> persistDeathCapture(snapshot, incident));
            return true;
        } catch (RejectedExecutionException exception) {
            metrics.recordRejectedCapture();
            metrics.recordFailedPersistence();
            logger.log(Level.WARNING,
                    "Evidence capture rejected (queue full): snapshot=" + snapshot.id()
                            + " incident=" + incident.id(), exception);
            return false;
        }
    }

    /**
     * Submits a post-respawn snapshot for asynchronous persistence and links it to
     * an existing incident. The snapshot insert and incident update are atomic.
     *
     * @return {@code true} if accepted, {@code false} if rejected
     */
    public boolean submitPostRespawnSnapshot(UUID incidentId, InventorySnapshot snapshot) {
        if (!running) {
            return false;
        }

        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(snapshot, "snapshot");

        try {
            executor.submit(() -> persistPostRespawnSnapshot(incidentId, snapshot));
            return true;
        } catch (RejectedExecutionException exception) {
            metrics.recordRejectedCapture();
            logger.log(Level.WARNING,
                    "Post-respawn snapshot rejected (queue full): snapshot=" + snapshot.id()
                            + " incident=" + incidentId, exception);
            return false;
        }
    }

    public EvidenceMetrics.EvidenceMetricsSnapshot metricsSnapshot() {
        return metrics.snapshot();
    }

    public int pendingCount() {
        return executor.getQueue().size();
    }

    /**
     * Stops accepting work, drains pending tasks, and shuts down the executor.
     *
     * @return {@code true} if all pending work was completed before the timeout
     */
    public boolean shutdown(Duration timeout) {
        running = false;
        executor.shutdown();
        try {
            boolean finished = executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                int remaining = executor.shutdownNow().size();
                if (remaining > 0) {
                    logger.warning("Evidence writer abandoned " + remaining + " pending task(s)");
                }
            }
            return finished;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }

    private void persistDeathCapture(InventorySnapshot snapshot, Incident incident) {
        try {
            evidenceStore.insertDeathEvidence(snapshot, incident);
            metrics.recordSnapshotPersisted();
            metrics.recordIncidentPersisted();
        } catch (SQLException exception) {
            if (isDuplicateKeyViolation(exception)) {
                metrics.recordDuplicateIncident();
                logger.log(Level.FINE,
                        "Duplicate incident suppressed: " + incident.deduplicationKey(),
                        exception);
            } else {
                metrics.recordFailedPersistence();
                errorHandler.accept(exception);
            }
        } catch (RuntimeException runtimeException) {
            metrics.recordFailedPersistence();
            errorHandler.accept(runtimeException);
        }
    }

    private void persistPostRespawnSnapshot(UUID incidentId, InventorySnapshot snapshot) {
        try {
            evidenceStore.insertPostRespawnSnapshot(incidentId, snapshot);
            metrics.recordSnapshotPersisted();
        } catch (SQLException exception) {
            metrics.recordFailedPersistence();
            errorHandler.accept(exception);
        } catch (RuntimeException runtimeException) {
            metrics.recordFailedPersistence();
            errorHandler.accept(runtimeException);
        }
    }

    static boolean isDuplicateKeyViolation(SQLException exception) {
        if (exception == null) {
            return false;
        }
        int errorCode = exception.getErrorCode();
        String message = exception.getMessage();
        return errorCode == 19
                && message != null
                && message.contains("UNIQUE");
    }
}
