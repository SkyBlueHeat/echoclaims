package io.github.skyblueheat.echoclaims.domain.claim;

/**
 * The kind of action recorded in a {@link ClaimAuditEntry}.
 *
 * <p>Each value corresponds to a lifecycle event in the claim state machine.
 * Future sprints may add {@code APPROVED}, {@code REJECTED}, {@code REFUNDED},
 * but those are intentionally excluded from MVP-02.</p>
 */
public enum ClaimAction {
    CREATED,
    SUBMITTED,
    CANCELLED
}
