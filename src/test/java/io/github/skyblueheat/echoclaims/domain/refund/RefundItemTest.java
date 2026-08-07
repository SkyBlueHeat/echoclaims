package io.github.skyblueheat.echoclaims.domain.refund;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundItemTest {

    private static final UUID uuid = UUID.randomUUID();

    private static RefundItem item(int refundable, int delivered, RefundItemStatus status) {
        return new RefundItem(
                uuid, uuid, uuid, uuid, uuid,
                "snapshot-uuid:0",
                uuid,
                "diamond_sword",
                refundable, delivered,
                "base64data",
                status, "",
                1000L, 1000L, 0
        );
    }

    @Test
    void validPendingItem() {
        assertDoesNotThrow(() -> item(5, 0, RefundItemStatus.PENDING));
    }

    @Test
    void validPartiallyDeliveredItem() {
        assertDoesNotThrow(() -> item(5, 2, RefundItemStatus.PARTIALLY_DELIVERED));
    }

    @Test
    void validDeliveredItem() {
        assertDoesNotThrow(() -> item(5, 5, RefundItemStatus.DELIVERED));
    }

    @Test
    void validFailedItem() {
        assertDoesNotThrow(() -> item(5, 2, RefundItemStatus.FAILED));
    }

    @Test
    void pendingWithDeliveredQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(5, 1, RefundItemStatus.PENDING));
    }

    @Test
    void partiallyDeliveredWithZeroDeliveredRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(5, 0, RefundItemStatus.PARTIALLY_DELIVERED));
    }

    @Test
    void partiallyDeliveredWithFullDeliveredRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(5, 5, RefundItemStatus.PARTIALLY_DELIVERED));
    }

    @Test
    void deliveredWithPartialQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(5, 3, RefundItemStatus.DELIVERED));
    }

    @Test
    void zeroRefundableQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(0, 0, RefundItemStatus.PENDING));
    }

    @Test
    void negativeRefundableQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(-1, 0, RefundItemStatus.PENDING));
    }

    @Test
    void deliveredExceedingRefundableRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(3, 5, RefundItemStatus.PARTIALLY_DELIVERED));
    }

    @Test
    void blankEvidenceReferenceRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new RefundItem(uuid, uuid, uuid, uuid, uuid, "  ", uuid, "diamond", 1, 0,
                        "data", RefundItemStatus.PENDING, "", 1000, 1000, 0));
    }

    @Test
    void blankMaterialKeyRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new RefundItem(uuid, uuid, uuid, uuid, uuid, "ref", uuid, "  ", 1, 0,
                        "data", RefundItemStatus.PENDING, "", 1000, 1000, 0));
    }

    @Test
    void nullSerializedDataDefaultsToEmpty() {
        RefundItem ri = new RefundItem(uuid, uuid, uuid, uuid, uuid, "ref", uuid, "diamond", 1, 0,
                null, RefundItemStatus.PENDING, "", 1000, 1000, 0);
        assertFalse(ri.hasSerializedData());
    }

    @Test
    void remainingQuantityCalculatedCorrectly() {
        RefundItem ri = item(10, 3, RefundItemStatus.PARTIALLY_DELIVERED);
        assertEquals(7, ri.remainingQuantity());
    }

    @Test
    void remainingQuantityIsZeroWhenFullyDelivered() {
        RefundItem ri = item(5, 5, RefundItemStatus.DELIVERED);
        assertEquals(0, ri.remainingQuantity());
    }

    @Test
    void nullRequiredFieldsRejected() {
        assertThrows(NullPointerException.class, () ->
                new RefundItem(null, uuid, uuid, uuid, uuid, "ref", uuid, "diamond", 1, 0,
                        "data", RefundItemStatus.PENDING, "", 1000, 1000, 0));
        assertThrows(NullPointerException.class, () ->
                new RefundItem(uuid, uuid, uuid, uuid, uuid, "ref", uuid, "diamond", 1, 0,
                        "data", null, "", 1000, 1000, 0));
    }
}
