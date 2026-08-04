package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Central policy that enforces allowed state transitions for claims.
 *
 * <p>Every transition returns a {@link TransitionResult} that explains whether the
 * transition is allowed and, if not, provides a diagnostic reason. This ensures that
 * every rejected transition is explainable from recorded causes (rule 9).</p>
 *
 * <p>Allowed transitions in MVP-02:</p>
 * <ul>
 *   <li>{@code DRAFT → SUBMITTED} (player submits)</li>
 *   <li>{@code DRAFT → CANCELLED} (player cancels)</li>
 *   <li>{@code SUBMITTED → CANCELLED} (player cancels)</li>
 * </ul>
 *
 * <p>Transitions to the same status are rejected as no-ops. Transitions from
 * {@code CANCELLED} are always rejected (terminal state).</p>
 */
public final class ClaimTransitionPolicy {

    private ClaimTransitionPolicy() {
    }

    /**
     * Evaluates whether a transition from the claim's current status to {@code target} is allowed.
     *
     * @param claim the claim to transition
     * @param target the desired new status
     * @return a structured result indicating success or rejection with a reason
     */
    public static TransitionResult evaluate(Claim claim, ClaimStatus target) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(target, "target");

        ClaimStatus current = claim.status();

        if (current == target) {
            return TransitionResult.rejected("Claim is already " + current.name());
        }

        if (current == ClaimStatus.CANCELLED) {
            return TransitionResult.rejected("Claim is cancelled and cannot be transitioned");
        }

        if (current == ClaimStatus.DRAFT && target == ClaimStatus.SUBMITTED) {
            return TransitionResult.allowed(ClaimAction.SUBMITTED);
        }

        if (current == ClaimStatus.DRAFT && target == ClaimStatus.CANCELLED) {
            return TransitionResult.allowed(ClaimAction.CANCELLED);
        }

        if (current == ClaimStatus.SUBMITTED && target == ClaimStatus.CANCELLED) {
            return TransitionResult.allowed(ClaimAction.CANCELLED);
        }

        return TransitionResult.rejected(
                "Transition from " + current.name() + " to " + target.name() + " is not allowed");
    }

    /**
     * Structured result of a transition evaluation.
     */
    public static final class TransitionResult {
        private final boolean allowed;
        private final ClaimAction action;
        private final String rejectionReason;

        private TransitionResult(boolean allowed, ClaimAction action, String rejectionReason) {
            this.allowed = allowed;
            this.action = action;
            this.rejectionReason = rejectionReason;
        }

        static TransitionResult allowed(ClaimAction action) {
            return new TransitionResult(true, action, null);
        }

        static TransitionResult rejected(String reason) {
            return new TransitionResult(false, null, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Optional<ClaimAction> action() {
            return Optional.ofNullable(action);
        }

        public Optional<String> rejectionReason() {
            return Optional.ofNullable(rejectionReason);
        }
    }
}
