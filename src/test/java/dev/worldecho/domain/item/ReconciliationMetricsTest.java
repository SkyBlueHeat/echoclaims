package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationMetricsTest {

    @Test
    void allCountersStartAtZero() {
        ReconciliationMetrics metrics = new ReconciliationMetrics();
        assertEquals(0, metrics.inventoryReconciliations());
        assertEquals(0, metrics.automaticIdentitiesAssigned());
        assertEquals(0, metrics.automaticLotsAssigned());
        assertEquals(0, metrics.ownershipTransitionsRecorded());
        assertEquals(0, metrics.identityWarnings());
        assertEquals(0, metrics.duplicateIdentityObservations());
        assertEquals(0, metrics.pendingReconciliations());
    }

    @Test
    void recordReconciliationIncrements() {
        ReconciliationMetrics metrics = new ReconciliationMetrics();
        metrics.recordReconciliation();
        metrics.recordReconciliation();
        assertEquals(2, metrics.inventoryReconciliations());
    }

    @Test
    void pendingIncrementAndDecrement() {
        ReconciliationMetrics metrics = new ReconciliationMetrics();
        metrics.incrementPending();
        metrics.incrementPending();
        assertEquals(2, metrics.pendingReconciliations());
        metrics.decrementPending();
        assertEquals(1, metrics.pendingReconciliations());
    }

    @Test
    void resetZeroesAll() {
        ReconciliationMetrics metrics = new ReconciliationMetrics();
        metrics.recordReconciliation();
        metrics.recordIdentityAssigned();
        metrics.recordLotAssigned();
        metrics.recordOwnershipTransition();
        metrics.recordIdentityWarning();
        metrics.recordDuplicateIdentity();
        metrics.incrementPending();
        metrics.reset();
        assertEquals(0, metrics.inventoryReconciliations());
        assertEquals(0, metrics.automaticIdentitiesAssigned());
        assertEquals(0, metrics.automaticLotsAssigned());
        assertEquals(0, metrics.ownershipTransitionsRecorded());
        assertEquals(0, metrics.identityWarnings());
        assertEquals(0, metrics.duplicateIdentityObservations());
        assertEquals(0, metrics.pendingReconciliations());
    }
}
