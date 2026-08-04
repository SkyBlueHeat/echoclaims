package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTransitionPolicyTest {

    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID PLAYER_UUID = UUID.randomUUID();

    private Claim claim(ClaimStatus status) {
        return new Claim(
                CLAIM_ID, "REF12345", INCIDENT_ID, PLAYER_UUID,
                status, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        );
    }

    @Test
    void draftToSubmittedIsAllowed() {
        var result = ClaimTransitionPolicy.evaluate(claim(ClaimStatus.DRAFT), ClaimStatus.SUBMITTED);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.SUBMITTED, result.action().orElseThrow());
    }

    @Test
    void draftToCancelledIsAllowed() {
        var result = ClaimTransitionPolicy.evaluate(claim(ClaimStatus.DRAFT), ClaimStatus.CANCELLED);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.CANCELLED, result.action().orElseThrow());
    }

    @Test
    void submittedToCancelledIsAllowed() {
        var result = ClaimTransitionPolicy.evaluate(claim(ClaimStatus.SUBMITTED), ClaimStatus.CANCELLED);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.CANCELLED, result.action().orElseThrow());
    }

    @Test
    void cancelledToAnyIsRejected() {
        var result = ClaimTransitionPolicy.evaluate(claim(ClaimStatus.CANCELLED), ClaimStatus.SUBMITTED);
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void sameStatusIsRejectedAsNoOp() {
        var result = ClaimTransitionPolicy.evaluate(claim(ClaimStatus.DRAFT), ClaimStatus.DRAFT);
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void submittedToDraftIsRejected() {
        var result = ClaimTransitionPolicy.evaluate(claim(ClaimStatus.SUBMITTED), ClaimStatus.DRAFT);
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }
}
