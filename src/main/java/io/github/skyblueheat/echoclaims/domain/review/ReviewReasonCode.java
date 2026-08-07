package io.github.skyblueheat.echoclaims.domain.review;

/**
 * Bounded reason codes for review decisions.
 *
 * <p>Using a bounded enum instead of unrestricted reason strings ensures
 * deterministic, auditable decision rationale. {@code OTHER} requires a
 * non-blank staff explanation.</p>
 *
 * <p>Reason codes do not claim integration evidence which EchoClaims does not
 * currently capture.</p>
 */
public enum ReviewReasonCode {
    EVIDENCE_CONFIRMS_LOSS,
    EVIDENCE_PARTIALLY_CONFIRMS_LOSS,
    ITEM_RECOVERED_BY_PLAYER,
    ITEM_NOT_SUPPORTED_BY_EVIDENCE,
    DUPLICATE_OR_ALREADY_COMPENSATED,
    SERVER_POLICY_EXCLUSION,
    INSUFFICIENT_EVIDENCE,
    OTHER
}
