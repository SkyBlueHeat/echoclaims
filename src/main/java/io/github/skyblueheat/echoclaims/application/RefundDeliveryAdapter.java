package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;

/**
 * Abstraction for the Paper-specific item delivery mechanism.
 *
 * <p>Implementations must perform inventory insertion on the server main thread.
 * The adapter is called by {@link RefundService} for each pending refund item.</p>
 *
 * <p>The adapter must:</p>
 * <ul>
 *   <li>Reconstruct the ItemStack from the refund item's serialized data or
 *       evidence reference using {@link io.github.skyblueheat.echoclaims.integration.ItemSerializer}.</li>
 *   <li>Set the correct amount (the remaining quantity to deliver, not the
 *       full refundable quantity).</li>
 *   <li>Insert the item into the player's inventory using
 *       {@code addItem()} which returns leftovers for items that don't fit.</li>
 *   <li>Return the quantity actually delivered (inserted minus leftovers).</li>
 *   <li>Never drop items on the ground as a fallback.</li>
 *   <li>If deserialization fails, return a failure result with a diagnostic
 *       reason. Never create a substitute item.</li>
 * </ul>
 */
public interface RefundDeliveryAdapter {

    /**
     * Attempts to deliver a single refund item to the player's inventory.
     *
     * @param item the refund item to deliver; only the remaining quantity
     *             ({@code item.refundableQuantity() - item.deliveredQuantity()})
     *             should be inserted
     * @return the delivery result indicating how many were actually inserted
     *         and whether an error occurred
     */
    ItemDeliveryResult deliverItem(RefundItem item);

    /**
     * Result of a single item delivery attempt.
     *
     * @param quantityDelivered the number of items actually inserted into the inventory
     * @param failure           whether an unrecoverable error occurred
     * @param failureReason     diagnostic reason if {@code failure} is true; empty otherwise
     */
    record ItemDeliveryResult(int quantityDelivered, boolean failure, String failureReason) {

        public ItemDeliveryResult {
            if (quantityDelivered < 0) {
                throw new IllegalArgumentException("quantityDelivered cannot be negative");
            }
            failureReason = failureReason == null ? "" : failureReason.strip();
            if (!failure && !failureReason.isEmpty()) {
                throw new IllegalArgumentException("failureReason must be empty when failure is false");
            }
        }

        public static ItemDeliveryResult success(int quantityDelivered) {
            return new ItemDeliveryResult(quantityDelivered, false, "");
        }

        public static ItemDeliveryResult partial(int quantityDelivered) {
            return new ItemDeliveryResult(quantityDelivered, false, "");
        }

        public static ItemDeliveryResult failure(String reason) {
            return new ItemDeliveryResult(0, true, reason);
        }
    }
}
