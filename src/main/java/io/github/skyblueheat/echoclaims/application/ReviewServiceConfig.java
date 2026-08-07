package io.github.skyblueheat.echoclaims.application;

/**
 * Immutable configuration for {@link ReviewService}.
 *
 * <p>Carries review-related limits and flags extracted from {@code EchoClaimsSettings}
 * so the service remains testable without a full settings record.</p>
 *
 * @param requireAssignmentForMutations whether only the assigned reviewer may mutate a review
 * @param maxInternalNoteLength         maximum character length for internal notes
 * @param maxQuestionLength             maximum character length for information requests
 * @param maxPlayerResponseLength       maximum character length for player responses
 * @param maxFinalSummaryLength         maximum character length for final summaries
 * @param maxItemNoteLength             maximum character length for item decision notes
 * @param maxItemDecisionsPerClaim      maximum number of item decisions per claim/review
 */
public record ReviewServiceConfig(
        boolean requireAssignmentForMutations,
        int maxInternalNoteLength,
        int maxQuestionLength,
        int maxPlayerResponseLength,
        int maxFinalSummaryLength,
        int maxItemNoteLength,
        int maxItemDecisionsPerClaim
) {

    public ReviewServiceConfig {
        maxInternalNoteLength = Math.max(1, Math.min(maxInternalNoteLength, 10_000));
        maxQuestionLength = Math.max(1, Math.min(maxQuestionLength, 10_000));
        maxPlayerResponseLength = Math.max(1, Math.min(maxPlayerResponseLength, 10_000));
        maxFinalSummaryLength = Math.max(1, Math.min(maxFinalSummaryLength, 10_000));
        maxItemNoteLength = Math.max(1, Math.min(maxItemNoteLength, 10_000));
        maxItemDecisionsPerClaim = Math.max(1, Math.min(maxItemDecisionsPerClaim, 1_000));
    }

    public static ReviewServiceConfig defaults() {
        return new ReviewServiceConfig(
                true,
                1_000,
                1_000,
                1_000,
                2_000,
                500,
                100
        );
    }
}
