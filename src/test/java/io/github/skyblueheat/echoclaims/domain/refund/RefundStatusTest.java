package io.github.skyblueheat.echoclaims.domain.refund;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundStatusTest {

    @Test
    void isTerminalReturnsTrueOnlyForCompleted() {
        assertTrue(RefundStatus.COMPLETED.isTerminal());
        assertFalse(RefundStatus.FAILED.isTerminal());
    }

    @Test
    void isTerminalReturnsFalseForNonTerminalStates() {
        assertFalse(RefundStatus.PENDING.isTerminal());
        assertFalse(RefundStatus.READY.isTerminal());
        assertFalse(RefundStatus.DELIVERING.isTerminal());
    }

    @Test
    void isDeliverableReturnsTrueForPendingAndReady() {
        assertTrue(RefundStatus.PENDING.isDeliverable());
        assertTrue(RefundStatus.READY.isDeliverable());
    }

    @Test
    void isDeliverableReturnsFalseForDeliveringCompletedAndFailed() {
        assertFalse(RefundStatus.DELIVERING.isDeliverable());
        assertFalse(RefundStatus.COMPLETED.isDeliverable());
        assertFalse(RefundStatus.FAILED.isDeliverable());
    }

    @Test
    void isRecoverableReturnsTrueOnlyForFailed() {
        assertTrue(RefundStatus.FAILED.isRecoverable());
        assertFalse(RefundStatus.COMPLETED.isRecoverable());
        assertFalse(RefundStatus.PENDING.isRecoverable());
        assertFalse(RefundStatus.READY.isRecoverable());
        assertFalse(RefundStatus.DELIVERING.isRecoverable());
    }
}
