package io.github.skyblueheat.echoclaims.domain.review;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewOutcomePolicyTest {

    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final String EVIDENCE_REF = "snapshot-uuid:0";

    private ClaimReviewItemDecision decision(int original, int approved, ReviewItemOutcome outcome) {
        return new ClaimReviewItemDecision(
                UUID.randomUUID(), REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF + ":" + original, SNAPSHOT_ID,
                original, approved, outcome,
                ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, java.util.Map.of()
        );
    }

    // --- derive() tests ---

    @Test
    void deriveReturnsEmptyForNoDecisions() {
        assertEquals(Optional.empty(), ReviewOutcomePolicy.derive(List.of()));
    }

    @Test
    void deriveReturnsApprovedWhenAllApproved() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 10, ReviewItemOutcome.APPROVED),
                decision(5, 5, ReviewItemOutcome.APPROVED)
        );
        assertEquals(Optional.of(ReviewOutcome.APPROVED), ReviewOutcomePolicy.derive(decisions));
    }

    @Test
    void deriveReturnsPartiallyApprovedWhenMixed() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 10, ReviewItemOutcome.APPROVED),
                decision(5, 2, ReviewItemOutcome.PARTIALLY_APPROVED)
        );
        assertEquals(Optional.of(ReviewOutcome.PARTIALLY_APPROVED), ReviewOutcomePolicy.derive(decisions));
    }

    @Test
    void deriveReturnsRejectedWhenAllRejected() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 0, ReviewItemOutcome.REJECTED),
                decision(5, 0, ReviewItemOutcome.REJECTED)
        );
        assertEquals(Optional.of(ReviewOutcome.REJECTED), ReviewOutcomePolicy.derive(decisions));
    }

    @Test
    void deriveReturnsPartiallyApprovedForSinglePartial() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 5, ReviewItemOutcome.PARTIALLY_APPROVED)
        );
        assertEquals(Optional.of(ReviewOutcome.PARTIALLY_APPROVED), ReviewOutcomePolicy.derive(decisions));
    }

    // --- validate() tests ---

    @Test
    void validateApprovedWithAllApprovedDecisions() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 10, ReviewItemOutcome.APPROVED)
        );
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.APPROVED, decisions);
        assertTrue(result.isAllowed());
    }

    @Test
    void validateApprovedRejectsEmptyDecisions() {
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.APPROVED, List.of());
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void validateApprovedRejectsMixedDecisions() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 10, ReviewItemOutcome.APPROVED),
                decision(5, 0, ReviewItemOutcome.REJECTED)
        );
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.APPROVED, decisions);
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void validatePartiallyApprovedWithMixedDecisions() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 10, ReviewItemOutcome.APPROVED),
                decision(5, 2, ReviewItemOutcome.PARTIALLY_APPROVED)
        );
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.PARTIALLY_APPROVED, decisions);
        assertTrue(result.isAllowed());
    }

    @Test
    void validatePartiallyApprovedRejectsAllApproved() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 10, ReviewItemOutcome.APPROVED)
        );
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.PARTIALLY_APPROVED, decisions);
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void validatePartiallyApprovedRejectsNoApprovedQuantity() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 0, ReviewItemOutcome.REJECTED)
        );
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.PARTIALLY_APPROVED, decisions);
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void validateRejectedWithAllRejectedDecisions() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 0, ReviewItemOutcome.REJECTED)
        );
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.REJECTED, decisions);
        assertTrue(result.isAllowed());
    }

    @Test
    void validateRejectedAllowsEmptyDecisions() {
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.REJECTED, List.of());
        assertTrue(result.isAllowed());
    }

    @Test
    void validateRejectedRejectsNonZeroApprovedQuantity() {
        List<ClaimReviewItemDecision> decisions = List.of(
                decision(10, 5, ReviewItemOutcome.PARTIALLY_APPROVED)
        );
        ReviewOutcomePolicy.ValidationResult result = ReviewOutcomePolicy.validate(
                ReviewOutcome.REJECTED, decisions);
        assertFalse(result.isAllowed());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void validateRejectsNullRequestedOutcome() {
        assertThrows(NullPointerException.class, () ->
                ReviewOutcomePolicy.validate(null, List.of()));
    }

    @Test
    void validateRejectsNullDecisions() {
        assertThrows(NullPointerException.class, () ->
                ReviewOutcomePolicy.validate(ReviewOutcome.APPROVED, null));
    }
}
