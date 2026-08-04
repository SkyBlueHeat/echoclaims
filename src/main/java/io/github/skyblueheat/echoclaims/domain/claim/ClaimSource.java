package io.github.skyblueheat.echoclaims.domain.claim;

/**
 * How a claim was created.
 *
 * <p>Only {@code PLAYER_COMMAND} is implemented in the MVP-02 sprint. Future
 * sources may include admin-created claims or GUI-initiated claims.</p>
 */
public enum ClaimSource {
    PLAYER_COMMAND
}
