package io.github.skyblueheat.echoclaims.domain.refund;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundTest {

    private static UUID uuid = UUID.randomUUID();

    private static Refund refund(RefundStatus status, long completedAt) {
        return new Refund(uuid, uuid, uuid, uuid, status, 1000L, completedAt, 0, Map.of());
    }

    @Test
    void validPendingRefund() {
        assertDoesNotThrow(() -> refund(RefundStatus.PENDING, 0));
    }

    @Test
    void completedRefundRequiresPositiveCompletedAt() {
        assertThrows(IllegalArgumentException.class, () -> refund(RefundStatus.COMPLETED, 0));
        assertDoesNotThrow(() -> refund(RefundStatus.COMPLETED, 5000L));
    }

    @Test
    void nonCompletedRefundRequiresZeroCompletedAt() {
        assertThrows(IllegalArgumentException.class, () -> refund(RefundStatus.READY, 5000L));
        assertThrows(IllegalArgumentException.class, () -> refund(RefundStatus.DELIVERING, 1L));
    }

    @Test
    void negativeCreatedAtRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Refund(uuid, uuid, uuid, uuid, RefundStatus.PENDING, -1, 0, 0, Map.of()));
    }

    @Test
    void negativeVersionRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Refund(uuid, uuid, uuid, uuid, RefundStatus.PENDING, 1000, 0, -1, Map.of()));
    }

    @Test
    void nullMetadataBecomesEmptyMap() {
        Refund r = new Refund(uuid, uuid, uuid, uuid, RefundStatus.PENDING, 1000, 0, 0, null);
        assertTrue(r.metadata().isEmpty());
    }

    @Test
    void metadataIsCopiedDefensively() {
        Map<String, String> mutable = new java.util.HashMap<>(Map.of("key", "value"));
        Refund r = new Refund(uuid, uuid, uuid, uuid, RefundStatus.PENDING, 1000, 0, 0, mutable);
        mutable.put("key2", "value2");
        assertEquals(1, r.metadata().size());
        assertEquals("value", r.metadata().get("key"));
    }

    @Test
    void blankMetadataKeysAreFiltered() {
        Refund r = new Refund(uuid, uuid, uuid, uuid, RefundStatus.PENDING, 1000, 0, 0,
                Map.of("", "value", "  ", "value2", "valid", "value3"));
        assertEquals(1, r.metadata().size());
        assertEquals("value3", r.metadata().get("valid"));
    }

    @Test
    void isPendingAndIsDeliverableWork() {
        Refund pending = refund(RefundStatus.PENDING, 0);
        assertTrue(pending.isPending());
        assertTrue(pending.isDeliverable());
        assertFalse(pending.isTerminal());
    }

    @Test
    void isCompletedAndIsTerminalWork() {
        Refund completed = refund(RefundStatus.COMPLETED, 5000L);
        assertTrue(completed.isCompleted());
        assertTrue(completed.isTerminal());
        assertFalse(completed.isDeliverable());
    }

    @Test
    void nullRequiredFieldsRejected() {
        assertThrows(NullPointerException.class, () ->
                new Refund(null, uuid, uuid, uuid, RefundStatus.PENDING, 0, 0, 0, Map.of()));
        assertThrows(NullPointerException.class, () ->
                new Refund(uuid, null, uuid, uuid, RefundStatus.PENDING, 0, 0, 0, Map.of()));
        assertThrows(NullPointerException.class, () ->
                new Refund(uuid, uuid, uuid, uuid, null, 0, 0, 0, Map.of()));
    }
}
