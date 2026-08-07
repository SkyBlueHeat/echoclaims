package io.github.skyblueheat.echoclaims.domain.refund;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundTransitionPolicyTest {

    private static Refund refund(RefundStatus status) {
        long completedAt = status == RefundStatus.COMPLETED ? 5000L : 0;
        return new Refund(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                status, 1000L, completedAt, 0, Map.of()
        );
    }

    @Test
    void pendingToReadyIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.PENDING), RefundStatus.READY);
        assertTrue(result.isAllowed());
    }

    @Test
    void pendingToDeliveringIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.PENDING), RefundStatus.DELIVERING);
        assertTrue(result.isAllowed());
    }

    @Test
    void pendingToFailedIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.PENDING), RefundStatus.FAILED);
        assertTrue(result.isAllowed());
    }

    @Test
    void pendingToCompletedIsRejected() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.PENDING), RefundStatus.COMPLETED);
        assertFalse(result.isAllowed());
        assertEquals("PENDING_TO_COMPLETED", result.reasonCode().orElse(""));
    }

    @Test
    void readyToDeliveringIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.READY), RefundStatus.DELIVERING);
        assertTrue(result.isAllowed());
    }

    @Test
    void readyToFailedIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.READY), RefundStatus.FAILED);
        assertTrue(result.isAllowed());
    }

    @Test
    void readyToCompletedIsRejected() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.READY), RefundStatus.COMPLETED);
        assertFalse(result.isAllowed());
        assertEquals("READY_TO_COMPLETED", result.reasonCode().orElse(""));
    }

    @Test
    void deliveringToCompletedIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.DELIVERING), RefundStatus.COMPLETED);
        assertTrue(result.isAllowed());
    }

    @Test
    void deliveringToReadyIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.DELIVERING), RefundStatus.READY);
        assertTrue(result.isAllowed());
    }

    @Test
    void deliveringToFailedIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.DELIVERING), RefundStatus.FAILED);
        assertTrue(result.isAllowed());
    }

    @Test
    void deliveringToPendingIsRejected() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.DELIVERING), RefundStatus.PENDING);
        assertFalse(result.isAllowed());
        assertEquals("DELIVERING_TO_PENDING", result.reasonCode().orElse(""));
    }

    @Test
    void failedToReadyIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.FAILED), RefundStatus.READY);
        assertTrue(result.isAllowed());
    }

    @Test
    void failedToPendingIsAllowed() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.FAILED), RefundStatus.PENDING);
        assertTrue(result.isAllowed());
    }

    @Test
    void failedToDeliveringIsRejected() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.FAILED), RefundStatus.DELIVERING);
        assertFalse(result.isAllowed());
        assertEquals("FAILED_TO_DELIVERING", result.reasonCode().orElse(""));
    }

    @Test
    void failedToCompletedIsRejected() {
        var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.FAILED), RefundStatus.COMPLETED);
        assertFalse(result.isAllowed());
        assertEquals("FAILED_TO_COMPLETED", result.reasonCode().orElse(""));
    }

    @Test
    void completedRejectsAllTransitions() {
        for (RefundStatus target : RefundStatus.values()) {
            if (target == RefundStatus.COMPLETED) continue; // same-state handled separately
            var result = RefundTransitionPolicy.evaluate(refund(RefundStatus.COMPLETED), target);
            assertFalse(result.isAllowed(), "COMPLETED should reject transition to " + target);
            assertEquals("TERMINAL_STATE", result.reasonCode().orElse(""));
        }
    }

    @Test
    void sameStateIsRejected() {
        for (RefundStatus status : RefundStatus.values()) {
            if (status == RefundStatus.COMPLETED) continue; // COMPLETED already rejects all
            var result = RefundTransitionPolicy.evaluate(refund(status), status);
            assertFalse(result.isAllowed(), status + " should reject same-state transition");
            assertEquals("SAME_STATE", result.reasonCode().orElse(""));
        }
    }
}
