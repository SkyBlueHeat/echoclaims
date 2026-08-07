package io.github.skyblueheat.echoclaims.domain.review;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Central, actor-aware policy that enforces allowed state transitions for claims.
 *
 * <p>Every transition returns a structured result that explains whether the
 * transition is allowed and, if not, provides a diagnostic reason code and
 * human-safe explanation. No command or service may directly mutate claim
 * status without passing through this policy.</p>
 *
 * <p>The policy considers:</p>
 * <ul>
 *   <li>current status</li>
 *   <li>target status</li>
 *   <li>actor type (PLAYER, STAFF)</li>
 *   <li>action being performed</li>
 *   <li>terminal-state information</li>
 * </ul>
 */
public final class ReviewTransitionPolicy {

    private ReviewTransitionPolicy() {
    }

    /**
     * Evaluates whether a transition from the claim's current status to
     * {@code target} is allowed for the given actor type.
     *
     * @param claim the claim to transition
     * @param target the desired new status
     * @param actorType the type of actor requesting the transition
     * @return a structured result indicating success or rejection with a reason
     */
    public static TransitionResult evaluate(
            Claim claim,
            ClaimStatus target,
            ClaimActorType actorType
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actorType, "actorType");

        ClaimStatus current = claim.status();

        if (current == target) {
            return TransitionResult.rejected("SAME_STATE",
                    "Claim is already " + current.name());
        }

        if (current.isTerminal()) {
            return TransitionResult.rejected("TERMINAL_STATE",
                    "Claim is in terminal state " + current.name()
                            + " and cannot be transitioned");
        }

        return switch (actorType) {
            case PLAYER -> evaluatePlayerTransition(current, target);
            case STAFF -> evaluateStaffTransition(current, target);
            case SYSTEM -> evaluateSystemTransition(current, target);
        };
    }

    private static TransitionResult evaluatePlayerTransition(ClaimStatus current, ClaimStatus target) {
        if (current == ClaimStatus.DRAFT && target == ClaimStatus.SUBMITTED) {
            return TransitionResult.allowed(ClaimAction.SUBMITTED);
        }
        if (current == ClaimStatus.DRAFT && target == ClaimStatus.CANCELLED) {
            return TransitionResult.allowed(ClaimAction.CANCELLED);
        }
        if (current == ClaimStatus.SUBMITTED && target == ClaimStatus.CANCELLED) {
            return TransitionResult.allowed(ClaimAction.CANCELLED);
        }
        if (current == ClaimStatus.WAITING_FOR_PLAYER && target == ClaimStatus.UNDER_REVIEW) {
            return TransitionResult.allowed(ClaimAction.PLAYER_RESPONDED);
        }
        if (target == ClaimStatus.CANCELLED && current.isUnderReview()) {
            return TransitionResult.rejected("PLAYER_CANCEL_AFTER_REVIEW_START",
                    "Player cannot cancel a claim once review has started");
        }
        return TransitionResult.rejected("PLAYER_TRANSITION_NOT_ALLOWED",
                "Player cannot transition from " + current.name() + " to " + target.name());
    }

    private static TransitionResult evaluateStaffTransition(ClaimStatus current, ClaimStatus target) {
        if (current == ClaimStatus.SUBMITTED && target == ClaimStatus.UNDER_REVIEW) {
            return TransitionResult.allowed(ClaimAction.REVIEW_STARTED);
        }
        if (current == ClaimStatus.UNDER_REVIEW && target == ClaimStatus.WAITING_FOR_PLAYER) {
            return TransitionResult.allowed(ClaimAction.INFORMATION_REQUESTED);
        }
        if (current == ClaimStatus.WAITING_FOR_PLAYER && target == ClaimStatus.UNDER_REVIEW) {
            return TransitionResult.allowed(ClaimAction.PLAYER_RESPONDED);
        }
        if (current == ClaimStatus.UNDER_REVIEW && target == ClaimStatus.APPROVED) {
            return TransitionResult.allowed(ClaimAction.REVIEW_APPROVED);
        }
        if (current == ClaimStatus.UNDER_REVIEW && target == ClaimStatus.PARTIALLY_APPROVED) {
            return TransitionResult.allowed(ClaimAction.REVIEW_PARTIALLY_APPROVED);
        }
        if (current == ClaimStatus.UNDER_REVIEW && target == ClaimStatus.REJECTED) {
            return TransitionResult.allowed(ClaimAction.REVIEW_REJECTED);
        }
        return TransitionResult.rejected("STAFF_TRANSITION_NOT_ALLOWED",
                "Staff cannot transition from " + current.name() + " to " + target.name());
    }

    private static TransitionResult evaluateSystemTransition(ClaimStatus current, ClaimStatus target) {
        return TransitionResult.rejected("SYSTEM_TRANSITION_NOT_ALLOWED",
                "System cannot transition from " + current.name() + " to " + target.name());
    }

    /**
     * Structured result of a transition evaluation.
     */
    public static final class TransitionResult {
        private final boolean allowed;
        private final ClaimAction action;
        private final String reasonCode;
        private final String explanation;

        private TransitionResult(boolean allowed, ClaimAction action,
                                  String reasonCode, String explanation) {
            this.allowed = allowed;
            this.action = action;
            this.reasonCode = reasonCode;
            this.explanation = explanation;
        }

        static TransitionResult allowed(ClaimAction action) {
            return new TransitionResult(true, action, null, null);
        }

        static TransitionResult rejected(String reasonCode, String explanation) {
            return new TransitionResult(false, null, reasonCode, explanation);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Optional<ClaimAction> action() {
            return Optional.ofNullable(action);
        }

        public Optional<String> reasonCode() {
            return Optional.ofNullable(reasonCode);
        }

        public Optional<String> explanation() {
            return Optional.ofNullable(explanation);
        }
    }
}
