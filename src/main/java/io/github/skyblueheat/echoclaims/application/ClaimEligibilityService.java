package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Evaluates whether a player is eligible to create a claim for a given incident.
 *
 * <p>Eligibility rules:</p>
 * <ul>
 *   <li>The incident must exist.</li>
 *   <li>The incident must belong to the claiming player (ownership check).</li>
 *   <li>The incident must be in {@code OPEN} status.</li>
 *   <li>The incident must have a pre-event snapshot (evidence requirement).</li>
 * </ul>
 *
 * <p>Every rejection includes a diagnostic reason (rule 9: every generated consequence
 * must be explainable from recorded causes).</p>
 */
public final class ClaimEligibilityService {

    private ClaimEligibilityService() {
    }

    /**
     * Evaluates claim eligibility.
     *
     * @param incident the incident to claim against
     * @param playerUuid the player requesting the claim
     * @return a structured result indicating eligibility or rejection with a reason
     */
    public static EligibilityResult evaluate(Incident incident, java.util.UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        if (incident == null) {
            return EligibilityResult.rejected("Incident not found");
        }

        if (!incident.playerUuid().equals(playerUuid)) {
            return EligibilityResult.rejected("Incident does not belong to this player");
        }

        if (incident.status() != IncidentStatus.OPEN) {
            return EligibilityResult.rejected(
                    "Incident is " + incident.status().name() + ", not OPEN");
        }

        if (incident.preEventSnapshotUuid() == null) {
            return EligibilityResult.rejected("Incident has no pre-event snapshot");
        }

        return EligibilityResult.eligible();
    }

    public static final class EligibilityResult {
        private final boolean eligible;
        private final String rejectionReason;

        private EligibilityResult(boolean eligible, String rejectionReason) {
            this.eligible = eligible;
            this.rejectionReason = rejectionReason;
        }

        static EligibilityResult eligible() {
            return new EligibilityResult(true, null);
        }

        static EligibilityResult rejected(String reason) {
            return new EligibilityResult(false, reason);
        }

        public boolean isEligible() {
            return eligible;
        }

        public Optional<String> rejectionReason() {
            return Optional.ofNullable(rejectionReason);
        }
    }
}
