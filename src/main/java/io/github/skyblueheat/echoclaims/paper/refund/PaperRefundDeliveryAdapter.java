package io.github.skyblueheat.echoclaims.paper.refund;

import io.github.skyblueheat.echoclaims.application.RefundDeliveryAdapter;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.integration.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper-specific implementation of {@link RefundDeliveryAdapter}.
 *
 * <p>This adapter must be called on the server main thread because it accesses
 * live Bukkit inventory objects. The {@link io.github.skyblueheat.echoclaims.application.RefundService}
 * is responsible for ensuring this adapter is invoked synchronously.</p>
 *
 * <h2>Delivery Flow</h2>
 *
 * <ol>
 *   <li>Look up the player by UUID. If offline, return zero delivered (refund stays pending).</li>
 *   <li>Deserialize the item from {@code serializedItemData} using
 *       {@link ItemSerializer#deserialize(String)}.</li>
 *   <li>If deserialization fails, return a failure result with a diagnostic reason.
 *       Never create a substitute item.</li>
 *   <li>Set the ItemStack amount to the remaining quantity to deliver.</li>
 *   <li>Call {@link PlayerInventory#addItem(ItemStack...)} which inserts items
 *       and returns a map of leftover items that didn't fit.</li>
 *   <li>Calculate the actual delivered quantity as:
 *       {@code remaining - leftoverAmount}.</li>
 *   <li>Return the result with the actual delivered quantity.</li>
 * </ol>
 *
 * <h2>Overflow Safety</h2>
 *
 * <p>Items that don't fit in the inventory are never dropped on the ground.
 * The leftover quantity remains as the refund item's pending quantity, and
 * the player can claim it later when inventory space is available.</p>
 *
 * <h2>Crash Window</h2>
 *
 * <p>There is an inherent crash window between the {@code addItem} call and
 * the database commit of the delivery state. If the server crashes in this
 * window, the player will have the items but the database will not reflect
 * the delivery. This is documented in {@link io.github.skyblueheat.echoclaims.application.RefundService}.</p>
 */
public final class PaperRefundDeliveryAdapter implements RefundDeliveryAdapter {

    private final ItemSerializer itemSerializer;
    private final Logger logger;

    public PaperRefundDeliveryAdapter(ItemSerializer itemSerializer, Logger logger) {
        this.itemSerializer = Objects.requireNonNull(itemSerializer, "itemSerializer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public ItemDeliveryResult deliverItem(RefundItem item) {
        Objects.requireNonNull(item, "item");

        if (item.remainingQuantity() <= 0) {
            return ItemDeliveryResult.success(0);
        }

        org.bukkit.entity.Player player = Bukkit.getPlayer(item.playerUuid());
        if (player == null) {
            return ItemDeliveryResult.success(0);
        }

        if (!item.hasSerializedData()) {
            logger.warning("Refund item " + item.id()
                    + " has no serialized data — cannot restore item");
            return ItemDeliveryResult.failure(
                    "No serialized item data available for evidence reference: "
                            + item.evidenceItemReference());
        }

        ItemStack stack;
        try {
            stack = itemSerializer.deserialize(item.serializedItemData());
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Failed to deserialize refund item " + item.id()
                            + " (evidence: " + item.evidenceItemReference() + ")",
                    e);
            return ItemDeliveryResult.failure(
                    "Item deserialization failed: " + e.getMessage());
        }

        if (stack == null) {
            logger.warning("Refund item " + item.id()
                    + " deserialized to null — cannot deliver");
            return ItemDeliveryResult.failure(
                    "Deserialized item is null for evidence reference: "
                            + item.evidenceItemReference());
        }

        int amountToDeliver = item.remainingQuantity();
        stack.setAmount(amountToDeliver);

        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> leftovers = inventory.addItem(stack);

        int leftoverAmount = 0;
        for (ItemStack leftover : leftovers.values()) {
            leftoverAmount += leftover.getAmount();
        }

        int actuallyDelivered = amountToDeliver - leftoverAmount;

        if (actuallyDelivered == amountToDeliver) {
            return ItemDeliveryResult.success(actuallyDelivered);
        } else if (actuallyDelivered > 0) {
            return ItemDeliveryResult.partial(actuallyDelivered);
        } else {
            return ItemDeliveryResult.partial(0);
        }
    }
}
