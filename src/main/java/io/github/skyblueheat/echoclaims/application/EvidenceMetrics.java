package io.github.skyblueheat.echoclaims.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe counters for evidence capture and persistence.
 *
 * <p>All counters are {@link AtomicLong} so they can be safely updated from both the
 * server thread (on capture) and the persistence thread (on completion).</p>
 */
public final class EvidenceMetrics {

    private final AtomicLong acceptedCaptures = new AtomicLong();
    private final AtomicLong persistedSnapshots = new AtomicLong();
    private final AtomicLong persistedIncidents = new AtomicLong();
    private final AtomicLong duplicateIncidents = new AtomicLong();
    private final AtomicLong failedPersistence = new AtomicLong();
    private final AtomicLong rejectedCaptures = new AtomicLong();
    private final AtomicLong pendingPersistence = new AtomicLong();

    public void recordAcceptedCapture() {
        acceptedCaptures.incrementAndGet();
        pendingPersistence.incrementAndGet();
    }

    public void recordRejectedCapture() {
        rejectedCaptures.incrementAndGet();
    }

    public void recordSnapshotPersisted() {
        persistedSnapshots.incrementAndGet();
    }

    public void recordIncidentPersisted() {
        persistedIncidents.incrementAndGet();
        pendingPersistence.decrementAndGet();
    }

    public void recordDuplicateIncident() {
        duplicateIncidents.incrementAndGet();
        pendingPersistence.decrementAndGet();
    }

    public void recordFailedPersistence() {
        failedPersistence.incrementAndGet();
        pendingPersistence.decrementAndGet();
    }

    public long acceptedCaptures() {
        return acceptedCaptures.get();
    }

    public long persistedSnapshots() {
        return persistedSnapshots.get();
    }

    public long persistedIncidents() {
        return persistedIncidents.get();
    }

    public long duplicateIncidents() {
        return duplicateIncidents.get();
    }

    public long failedPersistence() {
        return failedPersistence.get();
    }

    public long rejectedCaptures() {
        return rejectedCaptures.get();
    }

    public long pendingPersistence() {
        return Math.max(0, pendingPersistence.get());
    }

    public EvidenceMetricsSnapshot snapshot() {
        return new EvidenceMetricsSnapshot(
                acceptedCaptures.get(),
                persistedSnapshots.get(),
                persistedIncidents.get(),
                duplicateIncidents.get(),
                failedPersistence.get(),
                rejectedCaptures.get(),
                pendingPersistence.get()
        );
    }

    public record EvidenceMetricsSnapshot(
            long acceptedCaptures,
            long persistedSnapshots,
            long persistedIncidents,
            long duplicateIncidents,
            long failedPersistence,
            long rejectedCaptures,
            long pendingPersistence
    ) {
    }
}
