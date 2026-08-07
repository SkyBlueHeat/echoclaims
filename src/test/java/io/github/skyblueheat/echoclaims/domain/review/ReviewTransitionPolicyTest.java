package io.github.skyblueheat.echoclaims.domain.review;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewTransitionPolicyTest {

    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID PLAYER_UUID = UUID.randomUUID();

    private Claim claim(ClaimStatus status) {
        return new Claim(
                CLAIM_ID, "REF12345", INCIDENT_ID, PLAYER_UUID,
                status, ClaimSource.PLAYER_COMMAND, "desc",
                10_000L, 10_000L, 0L, 0, Map.of()
        );
    }

    // --- Player transitions ---

    @Test
    void playerCanSubmitDraft() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.DRAFT), ClaimStatus.SUBMITTED, ClaimActorType.PLAYER);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.SUBMITTED, result.action().orElseThrow());
    }

    @Test
    void playerCanCancelDraft() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.DRAFT), ClaimStatus.CANCELLED, ClaimActorType.PLAYER);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.CANCELLED, result.action().orElseThrow());
    }

    @Test
    void playerCanCancelSubmitted() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.SUBMITTED), ClaimStatus.CANCELLED, ClaimActorType.PLAYER);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.CANCELLED, result.action().orElseThrow());
    }

    @Test
    void playerCannotCancelUnderReview() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.CANCELLED, ClaimActorType.PLAYER);
        assertFalse(result.isAllowed());
    }

    @Test
    void playerCannotCancelWaitingForPlayer() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.WAITING_FOR_PLAYER), ClaimStatus.CANCELLED, ClaimActorType.PLAYER);
        assertFalse(result.isAllowed());
    }

    @Test
    void playerCanRespondToInformationRequest() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.WAITING_FOR_PLAYER), ClaimStatus.UNDER_REVIEW, ClaimActorType.PLAYER);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.PLAYER_RESPONDED, result.action().orElseThrow());
    }

    @Test
    void playerCannotStartReview() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.SUBMITTED), ClaimStatus.UNDER_REVIEW, ClaimActorType.PLAYER);
        assertFalse(result.isAllowed());
    }

    @Test
    void playerCannotApprove() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.APPROVED, ClaimActorType.PLAYER);
        assertFalse(result.isAllowed());
    }

    @Test
    void playerCannotReject() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.REJECTED, ClaimActorType.PLAYER);
        assertFalse(result.isAllowed());
    }

    // --- Staff transitions ---

    @Test
    void staffCanStartReviewFromSubmitted() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.SUBMITTED), ClaimStatus.UNDER_REVIEW, ClaimActorType.STAFF);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.REVIEW_STARTED, result.action().orElseThrow());
    }

    @Test
    void staffCanRequestInfoFromUnderReview() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.WAITING_FOR_PLAYER, ClaimActorType.STAFF);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.INFORMATION_REQUESTED, result.action().orElseThrow());
    }

    @Test
    void staffCanRecordPlayerResponseFromWaiting() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.WAITING_FOR_PLAYER), ClaimStatus.UNDER_REVIEW, ClaimActorType.STAFF);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.PLAYER_RESPONDED, result.action().orElseThrow());
    }

    @Test
    void staffCanApproveFromUnderReview() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.APPROVED, ClaimActorType.STAFF);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.REVIEW_APPROVED, result.action().orElseThrow());
    }

    @Test
    void staffCanPartiallyApproveFromUnderReview() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.PARTIALLY_APPROVED, ClaimActorType.STAFF);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.REVIEW_PARTIALLY_APPROVED, result.action().orElseThrow());
    }

    @Test
    void staffCanRejectFromUnderReview() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.REJECTED, ClaimActorType.STAFF);
        assertTrue(result.isAllowed());
        assertEquals(ClaimAction.REVIEW_REJECTED, result.action().orElseThrow());
    }

    @Test
    void staffCannotCancelUnderReview() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.UNDER_REVIEW), ClaimStatus.CANCELLED, ClaimActorType.STAFF);
        assertFalse(result.isAllowed());
    }

    @Test
    void staffCannotStartReviewFromDraft() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.DRAFT), ClaimStatus.UNDER_REVIEW, ClaimActorType.STAFF);
        assertFalse(result.isAllowed());
    }

    @Test
    void staffCannotApproveFromWaitingForPlayer() {
        ReviewTransitionPolicy.TransitionResult result =
                ReviewTransitionPolicy.evaluate(claim(ClaimStatus.WAITING_FOR_PLAYER), ClaimStatus.APPROVED, ClaimActorType.STAFF);
        assertFalse(result.isAllowed());
    }

    // --- Terminal state rejection ---

    @Test
    void terminalStateRejectsAllTransitions() {
        for (ClaimStatus terminal : new ClaimStatus[]{
                ClaimStatus.APPROVED, ClaimStatus.PARTIALLY_APPROVED,
                ClaimStatus.REJECTED, ClaimStatus.CANCELLED
        }) {
            for (ClaimStatus target : ClaimStatus.values()) {
                if (terminal == target) continue;
                ReviewTransitionPolicy.TransitionResult result =
                        ReviewTransitionPolicy.evaluate(claim(terminal), target, ClaimActorType.STAFF);
                assertFalse(result.isAllowed(), "Terminal " + terminal + " should reject transition to " + target);
            }
        }
    }

    @Test
    void sameStateRejectsAll() {
        for (ClaimStatus status : ClaimStatus.values()) {
            ReviewTransitionPolicy.TransitionResult result =
                    ReviewTransitionPolicy.evaluate(claim(status), status, ClaimActorType.STAFF);
            assertFalse(result.isAllowed(), "Same-state " + status + " should be rejected");
        }
    }

    // --- System transitions ---

    @Test
    void systemCannotTransitionAnyState() {
        for (ClaimStatus current : ClaimStatus.values()) {
            if (current.isTerminal()) continue;
            for (ClaimStatus target : ClaimStatus.values()) {
                if (current == target) continue;
                ReviewTransitionPolicy.TransitionResult result =
                        ReviewTransitionPolicy.evaluate(claim(current), target, ClaimActorType.SYSTEM);
                assertFalse(result.isAllowed(), "System should not transition " + current + " to " + target);
            }
        }
    }
}
