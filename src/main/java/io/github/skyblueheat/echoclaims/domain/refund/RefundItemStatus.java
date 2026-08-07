package io.github.skyblueheat.echoclaims.domain.refund;

/**
 * Delivery state of a single {@link RefundItem}.
 *
 * <p>Each refund item tracks its own delivery progress independently.
 * This enables partial delivery: some items may be delivered while others
 * remain pending due to inventory space constraints.</p>
 *
 * <p>State transitions:</p>
 * <pre>
 * PENDING → DELIVERED (full quantity delivered)
 * PENDING → PARTIALLY_DELIVERED (some quantity delivered, more remains)
 * PARTIALLY_DELIVERED → DELIVERED (remaining quantity delivered)
 * PENDING → FAILED (unrecoverable error, e.g. invalid payload)
 * PARTIALLY_DELIVERED → FAILED (unrecoverable error on retry)
 * </pre>
 *
 * <p>Terminal states are {@code DELIVERED} and {@code FAILED}.</p>
 */
public enum RefundItemStatus {
    PENDING,
    PARTIALLY_DELIVERED,
    DELIVERED,
    FAILED;

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED;
    }

    public boolean isPending() {
        return this == PENDING || this == PARTIALLY_DELIVERED;
    }
}
