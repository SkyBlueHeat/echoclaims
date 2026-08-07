package io.github.skyblueheat.echoclaims.domain.review;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimReviewItemDecisionTest {

    private static final UUID DECISION_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final String EVIDENCE_REF = "snapshot-uuid:0";

    @Test
    void createsValidApprovedDecision() {
        assertDoesNotThrow(() -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void createsValidPartiallyApprovedDecision() {
        assertDoesNotThrow(() -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 5,
                ReviewItemOutcome.PARTIALLY_APPROVED, ReviewReasonCode.EVIDENCE_PARTIALLY_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void createsValidRejectedDecision() {
        assertDoesNotThrow(() -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 0,
                ReviewItemOutcome.REJECTED, ReviewReasonCode.ITEM_NOT_SUPPORTED_BY_EVIDENCE,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsApprovedWithMismatchedQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 5,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsPartiallyApprovedWithZeroApproved() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 0,
                ReviewItemOutcome.PARTIALLY_APPROVED, ReviewReasonCode.INSUFFICIENT_EVIDENCE,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsPartiallyApprovedWithFullApproved() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.PARTIALLY_APPROVED, ReviewReasonCode.INSUFFICIENT_EVIDENCE,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsRejectedWithNonZeroApproved() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 1,
                ReviewItemOutcome.REJECTED, ReviewReasonCode.ITEM_NOT_SUPPORTED_BY_EVIDENCE,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNegativeOriginalQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                -1, 0,
                ReviewItemOutcome.REJECTED, ReviewReasonCode.ITEM_NOT_SUPPORTED_BY_EVIDENCE,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsApprovedExceedingOriginal() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                5, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsBlankEvidenceItemReference() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                "  ", SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNullEvidenceItemReference() {
        assertThrows(NullPointerException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                null, SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsOtherReasonCodeWithoutStaffNote() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 0,
                ReviewItemOutcome.REJECTED, ReviewReasonCode.OTHER,
                "", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void acceptsOtherReasonCodeWithStaffNote() {
        assertDoesNotThrow(() -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 0,
                ReviewItemOutcome.REJECTED, ReviewReasonCode.OTHER,
                "Custom reason explanation", 10_000L, 10_000L, 0, Map.of()
        ));
    }

    @Test
    void stripsStaffNote() {
        ClaimReviewItemDecision decision = new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "  note  ", 10_000L, 10_000L, 0, Map.of()
        );
        assertEquals("note", decision.staffNote());
        assertTrue(decision.hasStaffNote());
    }

    @Test
    void nullStaffNoteBecomesEmpty() {
        ClaimReviewItemDecision decision = new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                null, 10_000L, 10_000L, 0, Map.of()
        );
        assertEquals("", decision.staffNote());
        assertFalse(decision.hasStaffNote());
    }

    @Test
    void rejectsNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, -1, Map.of()
        ));
    }

    @Test
    void metadataIsDefensivelyCopied() {
        Map<String, String> mutable = new java.util.HashMap<>(Map.of("k", "v"));
        ClaimReviewItemDecision decision = new ClaimReviewItemDecision(
                DECISION_ID, REVIEW_ID, CLAIM_ID,
                EVIDENCE_REF, SNAPSHOT_ID,
                10, 10,
                ReviewItemOutcome.APPROVED, ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", 10_000L, 10_000L, 0, mutable
        );
        mutable.put("k2", "v2");
        assertEquals(1, decision.metadata().size());
        assertTrue(decision.hasMetadata());
    }
}
