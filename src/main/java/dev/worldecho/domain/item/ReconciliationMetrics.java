package dev.worldecho.domain.item;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics for automatic item tracking reconciliation.
 */
public final class ReconciliationMetrics {

    private final AtomicLong inventoryReconciliations = new AtomicLong();
    private final AtomicLong automaticIdentitiesAssigned = new AtomicLong();
    private final AtomicLong automaticLotsAssigned = new AtomicLong();
    private final AtomicLong ownershipTransitionsRecorded = new AtomicLong();
    private final AtomicLong identityWarnings = new AtomicLong();
    private final AtomicLong duplicateIdentityObservations = new AtomicLong();
    private final AtomicLong pendingReconciliations = new AtomicLong();

    public void recordReconciliation() {
        inventoryReconciliations.incrementAndGet();
    }

    public void recordIdentityAssigned() {
        automaticIdentitiesAssigned.incrementAndGet();
    }

    public void recordLotAssigned() {
        automaticLotsAssigned.incrementAndGet();
    }

    public void recordOwnershipTransition() {
        ownershipTransitionsRecorded.incrementAndGet();
    }

    public void recordIdentityWarning() {
        identityWarnings.incrementAndGet();
    }

    public void recordDuplicateIdentity() {
        duplicateIdentityObservations.incrementAndGet();
    }

    public void incrementPending() {
        pendingReconciliations.incrementAndGet();
    }

    public void decrementPending() {
        pendingReconciliations.decrementAndGet();
    }

    public long inventoryReconciliations() {
        return inventoryReconciliations.get();
    }

    public long automaticIdentitiesAssigned() {
        return automaticIdentitiesAssigned.get();
    }

    public long automaticLotsAssigned() {
        return automaticLotsAssigned.get();
    }

    public long ownershipTransitionsRecorded() {
        return ownershipTransitionsRecorded.get();
    }

    public long identityWarnings() {
        return identityWarnings.get();
    }

    public long duplicateIdentityObservations() {
        return duplicateIdentityObservations.get();
    }

    public long pendingReconciliations() {
        return pendingReconciliations.get();
    }

    public void reset() {
        inventoryReconciliations.set(0);
        automaticIdentitiesAssigned.set(0);
        automaticLotsAssigned.set(0);
        ownershipTransitionsRecorded.set(0);
        identityWarnings.set(0);
        duplicateIdentityObservations.set(0);
        pendingReconciliations.set(0);
    }
}
