package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.review.ReviewTransitionPolicy;

import java.util.Objects;
import java.util.Optional;

/**
 * Central, actor-aware policy that enforces allowed state transitions for claims.
 *
 * <p>Delegates to {@link ReviewTransitionPolicy} in the domain layer. Every
 * transition returns a {@link TransitionResult} that explains whether the
 * transition is allowed and, if not, provides a diagnostic reason code and
 * human-safe explanation.</p>
 *
 * <p>Allowed transitions:</p>
 * <ul>
 *   <li>{@code DRAFT → SUBMITTED} (player submits)</li>
 *   <li>{@code DRAFT → CANCELLED} (player cancels)</li>
 *   <li>{@code SUBMITTED → CANCELLED} (player cancels)</li>
 *   <li>{@code SUBMITTED → UNDER_REVIEW} (staff starts review)</li>
 *   <li>{@code UNDER_REVIEW → WAITING_FOR_PLAYER} (staff requests info)</li>
 *   <li>{@code WAITING_FOR_PLAYER → UNDER_REVIEW} (player responds)</li>
 *   <li>{@code UNDER_REVIEW → APPROVED} (staff approves)</li>
 *   <li>{@code UNDER_REVIEW → PARTIALLY_APPROVED} (staff partially approves)</li>
 *   <li>{@code UNDER_REVIEW → REJECTED} (staff rejects)</li>
 * </ul>
 *
 * <p>Terminal states ({@code APPROVED}, {@code PARTIALLY_APPROVED},
 * {@code REJECTED}, {@code CANCELLED}) reject all transitions.</p>
 */
public final class ClaimTransitionPolicy {

    private ClaimTransitionPolicy() {
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
    public static TransitionResult evaluate(Claim claim, ClaimStatus target, ClaimActorType actorType) {
        ReviewTransitionPolicy.TransitionResult domainResult =
                ReviewTransitionPolicy.evaluate(claim, target, actorType);
        if (domainResult.isAllowed()) {
            return TransitionResult.allowed(domainResult.action().orElseThrow());
        }
        return TransitionResult.rejected(
                domainResult.explanation().orElse("Transition not allowed"));
    }

    /**
     * Convenience overload that defaults to {@code PLAYER} actor type.
     * Maintained for backwards compatibility with MVP-02 callers.
     */
    public static TransitionResult evaluate(Claim claim, ClaimStatus target) {
        return evaluate(claim, target, ClaimActorType.PLAYER);
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
