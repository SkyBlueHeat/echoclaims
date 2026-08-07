package io.github.skyblueheat.echoclaims.domain.review;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic policy that derives the final review outcome from item decisions.
 *
 * <p>Staff must not manually choose an outcome inconsistent with item decisions.
 * This policy validates the requested outcome against the actual item decisions
 * and returns a structured result.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>{@code APPROVED}: requires at least one item decision; all must be fully APPROVED</li>
 *   <li>{@code PARTIALLY_APPROVED}: requires at least one approved quantity > 0;
 *       at least one item is partial or rejected, or an approved quantity is less than original</li>
 *   <li>{@code REJECTED}: approved quantity across all decisions must be 0;
 *       may be finalized without item decisions when the complete claim is unsupported</li>
 * </ul>
 */
public final class ReviewOutcomePolicy {

    private ReviewOutcomePolicy() {
    }

    /**
     * Derives the required outcome from the given item decisions.
     *
     * @param decisions the item decisions (may be empty)
     * @return the derived outcome, or empty if no decisions exist
     */
    public static Optional<ReviewOutcome> derive(List<ClaimReviewItemDecision> decisions) {
        Objects.requireNonNull(decisions, "decisions");
        if (decisions.isEmpty()) {
            return Optional.empty();
        }

        boolean allApproved = true;
        boolean anyApprovedQuantity = false;
        boolean anyPartialOrRejected = false;

        for (ClaimReviewItemDecision decision : decisions) {
            if (decision.outcome() == ReviewItemOutcome.APPROVED) {
                anyApprovedQuantity = true;
            } else if (decision.outcome() == ReviewItemOutcome.PARTIALLY_APPROVED) {
                anyApprovedQuantity = true;
                anyPartialOrRejected = true;
                allApproved = false;
            } else {
                anyPartialOrRejected = true;
                allApproved = false;
            }
        }

        if (allApproved) {
            return Optional.of(ReviewOutcome.APPROVED);
        }
        if (anyApprovedQuantity) {
            return Optional.of(ReviewOutcome.PARTIALLY_APPROVED);
        }
        return Optional.of(ReviewOutcome.REJECTED);
    }

    /**
     * Validates that the requested outcome is consistent with the item decisions.
     *
     * @param requestedOutcome the outcome the staff member wants to apply
     * @param decisions the item decisions
     * @return a structured validation result
     */
    public static ValidationResult validate(
            ReviewOutcome requestedOutcome,
            List<ClaimReviewItemDecision> decisions
    ) {
        Objects.requireNonNull(requestedOutcome, "requestedOutcome");
        Objects.requireNonNull(decisions, "decisions");

        return switch (requestedOutcome) {
            case APPROVED -> {
                if (decisions.isEmpty()) {
                    yield ValidationResult.rejected("APPROVED requires at least one item decision");
                }
                boolean allApproved = decisions.stream()
                        .allMatch(d -> d.outcome() == ReviewItemOutcome.APPROVED);
                if (!allApproved) {
                    yield ValidationResult.rejected(
                            "APPROVED requires all item decisions to be fully APPROVED");
                }
                yield ValidationResult.allowed();
            }
            case PARTIALLY_APPROVED -> {
                boolean anyApprovedQuantity = decisions.stream()
                        .anyMatch(d -> d.approvedQuantity() > 0);
                if (!anyApprovedQuantity) {
                    yield ValidationResult.rejected(
                            "PARTIALLY_APPROVED requires at least one approved quantity greater than zero");
                }
                boolean allFullyApproved = decisions.stream()
                        .allMatch(d -> d.outcome() == ReviewItemOutcome.APPROVED);
                if (allFullyApproved && !decisions.isEmpty()) {
                    yield ValidationResult.rejected(
                            "PARTIALLY_APPROVED requires at least one partial or rejected item decision");
                }
                yield ValidationResult.allowed();
            }
            case REJECTED -> {
                long totalApproved = decisions.stream()
                        .mapToLong(ClaimReviewItemDecision::approvedQuantity)
                        .sum();
                if (totalApproved > 0) {
                    yield ValidationResult.rejected(
                            "REJECTED requires approved quantity across all decisions to be zero");
                }
                yield ValidationResult.allowed();
            }
        };
    }

    public static final class ValidationResult {
        private final boolean allowed;
        private final String rejectionReason;

        private ValidationResult(boolean allowed, String rejectionReason) {
            this.allowed = allowed;
            this.rejectionReason = rejectionReason;
        }

        static ValidationResult allowed() {
            return new ValidationResult(true, null);
        }

        static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Optional<String> rejectionReason() {
            return Optional.ofNullable(rejectionReason);
        }
    }
}
