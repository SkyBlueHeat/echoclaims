package io.github.skyblueheat.echoclaims.domain.refund;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure state machine that enforces allowed transitions for {@link Refund}.
 *
 * <p>No command or service may directly mutate refund status without passing
 * through this policy. Every transition returns a structured result that
 * explains whether the transition is allowed and, if not, provides a
 * diagnostic reason code and human-safe explanation.</p>
 *
 * <p>Allowed transitions:</p>
 * <ul>
 *   <li>{@code PENDING → READY} — player is online, delivery can be attempted</li>
 *   <li>{@code PENDING → DELIVERING} — staff directly executes delivery</li>
 *   <li>{@code PENDING → FAILED} — unrecoverable error (e.g. invalid payload)</li>
 *   <li>{@code READY → DELIVERING} — delivery in progress</li>
 *   <li>{@code READY → FAILED} — unrecoverable error</li>
 *   <li>{@code DELIVERING → READY} — partial delivery, more items remain pending</li>
 *   <li>{@code DELIVERING → COMPLETED} — all items fully delivered</li>
 *   <li>{@code DELIVERING → FAILED} — unrecoverable error during delivery</li>
 *   <li>{@code FAILED → READY} — retry after failure (manual intervention)</li>
 *   <li>{@code FAILED → PENDING} — retry after failure</li>
 * </ul>
 *
 * <p>Only {@code COMPLETED} is permanently terminal and rejects all
 * transitions. {@code FAILED} is recoverable: transitions to {@code READY}
 * or {@code PENDING} are allowed for retry purposes.</p>
 */
public final class RefundTransitionPolicy {

    private RefundTransitionPolicy() {
    }

    /**
     * Evaluates whether a transition from the refund's current status to
     * {@code target} is allowed.
     *
     * @param refund the refund to transition
     * @param target the desired new status
     * @return a structured result indicating success or rejection with a reason
     */
    public static TransitionResult evaluate(Refund refund, RefundStatus target) {
        Objects.requireNonNull(refund, "refund");
        Objects.requireNonNull(target, "target");

        RefundStatus current = refund.status();

        if (current == target) {
            return TransitionResult.rejected("SAME_STATE",
                    "Refund is already " + current.name());
        }

        if (current == RefundStatus.COMPLETED) {
            return TransitionResult.rejected("TERMINAL_STATE",
                    "Refund is COMPLETED and cannot be transitioned");
        }

        return switch (current) {
            case PENDING -> evaluatePendingTransition(target);
            case READY -> evaluateReadyTransition(target);
            case DELIVERING -> evaluateDeliveringTransition(target);
            case FAILED -> evaluateFailedTransition(target);
            case COMPLETED -> TransitionResult.rejected("TERMINAL_STATE",
                    "Refund is COMPLETED and cannot be transitioned");
        };
    }

    private static TransitionResult evaluatePendingTransition(RefundStatus target) {
        return switch (target) {
            case READY -> TransitionResult.allowed();
            case DELIVERING -> TransitionResult.allowed();
            case FAILED -> TransitionResult.allowed();
            case COMPLETED -> TransitionResult.rejected("PENDING_TO_COMPLETED",
                    "Cannot transition directly from PENDING to COMPLETED");
            case PENDING -> TransitionResult.rejected("SAME_STATE",
                    "Refund is already PENDING");
        };
    }

    private static TransitionResult evaluateReadyTransition(RefundStatus target) {
        return switch (target) {
            case DELIVERING -> TransitionResult.allowed();
            case FAILED -> TransitionResult.allowed();
            case PENDING -> TransitionResult.allowed();
            case COMPLETED -> TransitionResult.rejected("READY_TO_COMPLETED",
                    "Cannot transition directly from READY to COMPLETED");
            case READY -> TransitionResult.rejected("SAME_STATE",
                    "Refund is already READY");
        };
    }

    private static TransitionResult evaluateDeliveringTransition(RefundStatus target) {
        return switch (target) {
            case COMPLETED -> TransitionResult.allowed();
            case READY -> TransitionResult.allowed();
            case FAILED -> TransitionResult.allowed();
            case PENDING -> TransitionResult.rejected("DELIVERING_TO_PENDING",
                    "Cannot transition from DELIVERING back to PENDING");
            case DELIVERING -> TransitionResult.rejected("SAME_STATE",
                    "Refund is already DELIVERING");
        };
    }

    private static TransitionResult evaluateFailedTransition(RefundStatus target) {
        return switch (target) {
            case READY -> TransitionResult.allowed();
            case PENDING -> TransitionResult.allowed();
            case DELIVERING -> TransitionResult.rejected("FAILED_TO_DELIVERING",
                    "Cannot transition directly from FAILED to DELIVERING; retry to READY or PENDING first");
            case COMPLETED -> TransitionResult.rejected("FAILED_TO_COMPLETED",
                    "Cannot transition directly from FAILED to COMPLETED");
            case FAILED -> TransitionResult.rejected("SAME_STATE",
                    "Refund is already FAILED");
        };
    }

    /**
     * Structured result of a transition evaluation.
     */
    public static final class TransitionResult {
        private final boolean allowed;
        private final String reasonCode;
        private final String explanation;

        private TransitionResult(boolean allowed, String reasonCode, String explanation) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
            this.explanation = explanation;
        }

        static TransitionResult allowed() {
            return new TransitionResult(true, null, null);
        }

        static TransitionResult rejected(String reasonCode, String explanation) {
            return new TransitionResult(false, reasonCode, explanation);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Optional<String> reasonCode() {
            return Optional.ofNullable(reasonCode);
        }

        public Optional<String> explanation() {
            return Optional.ofNullable(explanation);
        }
    }
}
