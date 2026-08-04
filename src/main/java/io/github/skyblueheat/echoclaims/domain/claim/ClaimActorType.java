package io.github.skyblueheat.echoclaims.domain.claim;

/**
 * Identifies the kind of actor that performed a claim action.
 *
 * <p>Used in {@link ClaimAuditEntry} to record who caused each state change
 * without coupling to provider-specific identity types.</p>
 */
public enum ClaimActorType {
    PLAYER,
    STAFF,
    SYSTEM
}
