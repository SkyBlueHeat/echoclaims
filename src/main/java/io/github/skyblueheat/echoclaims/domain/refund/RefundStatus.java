package io.github.skyblueheat.echoclaims.domain.refund;

/**
 * Lifecycle state of a {@link Refund}.
 *
 * <p>A refund starts as {@code PENDING} when it is created from a finalized
 * review. It transitions to {@code READY} when the player is online and
 * delivery can be attempted. {@code DELIVERING} is a transient state used
 * while items are being inserted into the player's inventory. {@code COMPLETED}
 * is permanently terminal: all approved quantities have been safely delivered.
 * {@code FAILED} is a recoverable error state: if retry is enabled in
 * configuration, a FAILED refund can transition back to {@code READY} or
 * {@code PENDING} for another delivery attempt.</p>
 *
 * <p>State transitions:</p>
 * <pre>
 * PENDING → READY → DELIVERING → COMPLETED
 *                      ↓
 *                   READY (partial delivery, more items remain)
 *                      ↓
 *                   FAILED (recoverable error)
 * PENDING → FAILED (e.g. evidence payload invalid)
 * FAILED → READY / PENDING (retry, if enabled)
 * </pre>
 *
 * <p>Only {@code COMPLETED} is permanently terminal. {@code FAILED} is
 * recoverable: transitions out of FAILED are allowed for retry purposes.</p>
 */
public enum RefundStatus {
    PENDING,
    READY,
    DELIVERING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED;
    }

    public boolean isRecoverable() {
        return this == FAILED;
    }

    public boolean isDeliverable() {
        return this == PENDING || this == READY;
    }
}
