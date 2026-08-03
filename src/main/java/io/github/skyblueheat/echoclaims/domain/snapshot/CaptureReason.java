package io.github.skyblueheat.echoclaims.domain.snapshot;

/**
 * Why an inventory snapshot was captured.
 *
 * <p>The reason is recorded on every snapshot so the evidence chain can distinguish
 * pre-event state from post-event state and from manual administrative captures.</p>
 */
public enum CaptureReason {
    PRE_DEATH,
    POST_RESPAWN,
    MANUAL_ADMIN
}
