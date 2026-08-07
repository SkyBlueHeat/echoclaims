package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transitions a claim between lifecycle states with policy enforcement and atomic persistence.
 *
 * <p>Every transition is validated by {@link ClaimTransitionPolicy} before being persisted.
 * The transition and its audit entry are persisted atomically with optimistic concurrency
 * on the claim version. If the version has changed since the claim was read, the transition
 * fails with a concurrency conflict.</p>
 */
public final class ClaimTransitionService {

    private final ClaimStore claimStore;
    private final ClaimMetrics metrics;

    public ClaimTransitionService(ClaimStore claimStore, ClaimMetrics metrics) {
        this.claimStore = Objects.requireNonNull(claimStore, "claimStore");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Transitions a claim to a new status.
     *
     * @param claim the claim to transition (must have been read from the store)
     * @param target the desired new status
     * @param actorUuid the UUID of the player performing the transition
     * @param reason optional reason for the transition
     * @return a structured result indicating success or rejection
     */
    public TransitionResult transition(
            Claim claim,
            ClaimStatus target,
            UUID actorUuid,
            String reason
    ) {
        return transition(claim, target, ClaimActorType.PLAYER, actorUuid, reason);
    }

    public TransitionResult transition(
            Claim claim,
            ClaimStatus target,
            ClaimActorType actorType,
            UUID actorUuid,
            String reason
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorUuid, "actorUuid");

        ClaimTransitionPolicy.TransitionResult policyResult =
                ClaimTransitionPolicy.evaluate(claim, target, actorType);

        if (!policyResult.isAllowed()) {
            metrics.recordClaimRejected();
            return TransitionResult.rejected(policyResult.rejectionReason().orElse("Transition not allowed"));
        }

        long now = System.currentTimeMillis();
        long submittedAt = target == ClaimStatus.SUBMITTED ? now : claim.submittedAt();
        long cancelledAt = target == ClaimStatus.CANCELLED ? now : claim.cancelledAt();

        ClaimAction action = policyResult.action().orElseThrow();
        int newVersion = claim.version() + 1;

        ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                UUID.randomUUID(),
                claim.id(),
                actorType,
                actorUuid,
                action,
                reason == null ? "" : reason,
                now,
                newVersion,
                java.util.Map.of()
        );

        try {
            boolean success = claimStore.transitionClaim(
                    claim.id(),
                    target,
                    submittedAt,
                    cancelledAt,
                    claim.version(),
                    auditEntry
            );

            if (!success) {
                metrics.recordConcurrencyConflict();
                return TransitionResult.concurrencyConflict();
            }

            switch (target) {
                case SUBMITTED -> metrics.recordClaimSubmitted();
                case CANCELLED -> metrics.recordClaimCancelled();
                default -> { }
            }

            return TransitionResult.success(target, newVersion);

        } catch (SQLException exception) {
            return TransitionResult.error(exception.getMessage());
        }
    }

    public static final class TransitionResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final ClaimStatus newStatus;
        private final int newVersion;
        private final String rejectionReason;

        private TransitionResult(boolean success, boolean concurrencyConflict,
                                 ClaimStatus newStatus, int newVersion, String rejectionReason) {
            this.success = success;
            this.concurrencyConflict = concurrencyConflict;
            this.newStatus = newStatus;
            this.newVersion = newVersion;
            this.rejectionReason = rejectionReason;
        }

        static TransitionResult success(ClaimStatus newStatus, int newVersion) {
            return new TransitionResult(true, false, newStatus, newVersion, null);
        }

        static TransitionResult rejected(String reason) {
            return new TransitionResult(false, false, null, 0, reason);
        }

        static TransitionResult concurrencyConflict() {
            return new TransitionResult(false, true, null, 0,
                    "Claim was modified by another action; please try again");
        }

        static TransitionResult error(String message) {
            return new TransitionResult(false, false, null, 0,
                    "Database error: " + message);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isConcurrencyConflict() {
            return concurrencyConflict;
        }

        public Optional<ClaimStatus> newStatus() {
            return Optional.ofNullable(newStatus);
        }

        public int newVersion() {
            return newVersion;
        }

        public Optional<String> rejectionReason() {
            return Optional.ofNullable(rejectionReason);
        }
    }
}
