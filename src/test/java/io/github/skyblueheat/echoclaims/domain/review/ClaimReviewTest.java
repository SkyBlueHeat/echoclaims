package io.github.skyblueheat.echoclaims.domain.review;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimReviewTest {

    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID STAFF_UUID = UUID.randomUUID();

    @Test
    void createsValidOpenReview() {
        assertDoesNotThrow(() -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, Map.of()
        ));
    }

    @Test
    void createsValidFinalizedReview() {
        assertDoesNotThrow(() -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.FINALIZED, ReviewOutcome.APPROVED, "All approved",
                10_000L, 20_000L, 20_000L, 1, Map.of()
        ));
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class, () -> new ClaimReview(
                null, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNullClaimId() {
        assertThrows(NullPointerException.class, () -> new ClaimReview(
                REVIEW_ID, null, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNullReviewerUuid() {
        assertThrows(NullPointerException.class, () -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, null,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, Map.of()
        ));
    }

    @Test
    void rejectsFinalOutcomeOnOpenReview() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, ReviewOutcome.APPROVED, "",
                10_000L, 10_000L, 0L, 0, Map.of()
        ));
    }

    @Test
    void rejectsFinalizedAtOnOpenReview() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 5_000L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNullFinalOutcomeOnFinalizedReview() {
        assertThrows(NullPointerException.class, () -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.FINALIZED, null, "Summary",
                10_000L, 20_000L, 20_000L, 1, Map.of()
        ));
    }

    @Test
    void rejectsZeroFinalizedAtOnFinalizedReview() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.FINALIZED, ReviewOutcome.REJECTED, "Summary",
                10_000L, 20_000L, 0L, 1, Map.of()
        ));
    }

    @Test
    void rejectsNegativeStartedAt() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                -1L, 10_000L, 0L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, -1, Map.of()
        ));
    }

    @Test
    void stripsFinalSummary() {
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "  summary  ",
                10_000L, 10_000L, 0L, 0, Map.of()
        );
        assertEquals("summary", review.finalSummary());
    }

    @Test
    void nullFinalSummaryBecomesEmpty() {
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, null,
                10_000L, 10_000L, 0L, 0, Map.of()
        );
        assertEquals("", review.finalSummary());
    }

    @Test
    void isOpenReturnsTrueForOpenState() {
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, Map.of()
        );
        assertTrue(review.isOpen());
        assertFalse(review.isFinalized());
    }

    @Test
    void isFinalizedReturnsTrueForFinalizedState() {
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.FINALIZED, ReviewOutcome.APPROVED, "Done",
                10_000L, 20_000L, 20_000L, 1, Map.of()
        );
        assertTrue(review.isFinalized());
        assertFalse(review.isOpen());
    }

    @Test
    void hasFinalSummaryReturnsTrueWhenNonEmpty() {
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "some summary",
                10_000L, 10_000L, 0L, 0, Map.of()
        );
        assertTrue(review.hasFinalSummary());
    }

    @Test
    void hasFinalSummaryReturnsFalseWhenEmpty() {
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, Map.of()
        );
        assertFalse(review.hasFinalSummary());
    }

    @Test
    void metadataIsDefensivelyCopied() {
        Map<String, String> mutable = new java.util.HashMap<>(Map.of("key", "value"));
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, mutable
        );
        mutable.put("key2", "value2");
        assertEquals(1, review.metadata().size());
        assertTrue(review.hasMetadata());
    }

    @Test
    void nullMetadataBecomesEmptyMap() {
        ClaimReview review = new ClaimReview(
                REVIEW_ID, CLAIM_ID, STAFF_UUID,
                ReviewState.OPEN, null, "",
                10_000L, 10_000L, 0L, 0, null
        );
        assertFalse(review.hasMetadata());
        assertTrue(review.metadata().isEmpty());
    }
}
