package io.github.skyblueheat.echoclaims.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewServiceConfigTest {

    @Test
    void defaultsAreSensible() {
        ReviewServiceConfig config = ReviewServiceConfig.defaults();
        assertTrue(config.requireAssignmentForMutations());
        assertEquals(1_000, config.maxInternalNoteLength());
        assertEquals(1_000, config.maxQuestionLength());
        assertEquals(1_000, config.maxPlayerResponseLength());
        assertEquals(2_000, config.maxFinalSummaryLength());
        assertEquals(500, config.maxItemNoteLength());
        assertEquals(100, config.maxItemDecisionsPerClaim());
    }

    @Test
    void clampsMaxInternalNoteLength() {
        ReviewServiceConfig config = new ReviewServiceConfig(
                true, 0, 1_000, 1_000, 2_000, 500, 100);
        assertEquals(1, config.maxInternalNoteLength());
    }

    @Test
    void clampsMaxItemDecisionsPerClaim() {
        ReviewServiceConfig config = new ReviewServiceConfig(
                true, 1_000, 1_000, 1_000, 2_000, 500, 100_000);
        assertEquals(1_000, config.maxItemDecisionsPerClaim());
    }

    @Test
    void clampsAllToMinimumOne() {
        ReviewServiceConfig config = new ReviewServiceConfig(
                false, -1, -1, -1, -1, -1, -1);
        assertEquals(1, config.maxInternalNoteLength());
        assertEquals(1, config.maxQuestionLength());
        assertEquals(1, config.maxPlayerResponseLength());
        assertEquals(1, config.maxFinalSummaryLength());
        assertEquals(1, config.maxItemNoteLength());
        assertEquals(1, config.maxItemDecisionsPerClaim());
        assertFalse(config.requireAssignmentForMutations());
    }
}
