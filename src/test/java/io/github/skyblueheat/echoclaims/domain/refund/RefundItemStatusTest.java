package io.github.skyblueheat.echoclaims.domain.refund;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundItemStatusTest {

    @Test
    void isTerminalReturnsTrueForDeliveredAndFailed() {
        assertTrue(RefundItemStatus.DELIVERED.isTerminal());
        assertTrue(RefundItemStatus.FAILED.isTerminal());
    }

    @Test
    void isTerminalReturnsFalseForPendingAndPartiallyDelivered() {
        assertFalse(RefundItemStatus.PENDING.isTerminal());
        assertFalse(RefundItemStatus.PARTIALLY_DELIVERED.isTerminal());
    }

    @Test
    void isPendingReturnsTrueForPendingAndPartiallyDelivered() {
        assertTrue(RefundItemStatus.PENDING.isPending());
        assertTrue(RefundItemStatus.PARTIALLY_DELIVERED.isPending());
    }

    @Test
    void isPendingReturnsFalseForDeliveredAndFailed() {
        assertFalse(RefundItemStatus.DELIVERED.isPending());
        assertFalse(RefundItemStatus.FAILED.isPending());
    }
}
