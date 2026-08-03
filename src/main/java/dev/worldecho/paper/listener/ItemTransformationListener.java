package dev.worldecho.paper.listener;

import dev.worldecho.config.WorldEchoSettings;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TransformationDecision;
import dev.worldecho.paper.item.ItemIdentityAdapter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Preserves WorldEcho tracked-item identity across anvil, smithing table,
 * and grindstone transformations.
 *
 * <p>Two-phase approach:
 * <ol>
 *   <li>At {@link EventPriority#LOW} — before the transaction completes — read
 *       the identity from the source item(s) and store it per-player.</li>
 *   <li>At {@link EventPriority#MONITOR} — after the transaction completes —
 *       check the cursor for the result item and write the captured identity.</li>
 * </ol>
 *
 * <p>Decision logic (whether to capture, where to write) is delegated to
 * {@link TransformationDecision} so it is unit-testable without Bukkit.
 */
public final class ItemTransformationListener implements Listener {

    private final Supplier<WorldEchoSettings> settingsSupplier;
    private final ItemIdentityAdapter identityAdapter;
    private final Map<UUID, TrackedItemId> pendingTransforms = new ConcurrentHashMap<>();

    public ItemTransformationListener(
            Supplier<WorldEchoSettings> settingsSupplier,
            ItemIdentityAdapter identityAdapter
    ) {
        this.settingsSupplier = settingsSupplier;
        this.identityAdapter = identityAdapter;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClickLow(InventoryClickEvent event) {
        WorldEchoSettings settings = settingsSupplier.get();
        if (!TransformationDecision.isFeatureEnabled(
                settings.automaticTrackingEnabled(),
                settings.automaticTrackingPlayerInventories(),
                settings.automaticTrackingTransformIdentityContinuity())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        InventoryType type = inventory.getType();

        TransformationDecision.CaptureDecision capture = TransformationDecision.shouldCaptureIdentity(
                type.name(), event.getAction().name(), event.getRawSlot());
        if (!capture.shouldCapture()) {
            return;
        }

        TrackedItemId sourceId = readSourceIdentity(inventory, capture.inventoryKind());
        if (sourceId != null) {
            pendingTransforms.put(player.getUniqueId(), sourceId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClickMonitor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        TrackedItemId sourceId = pendingTransforms.remove(player.getUniqueId());
        if (sourceId == null) {
            return;
        }

        ItemStack cursor = event.getCursor();
        boolean cursorPresent = cursor != null;
        boolean cursorIsAir = cursor == null || cursor.getType().isAir();

        InventoryType type = event.getInventory().getType();
        TransformationDecision.InventoryKind kind = TransformationDecision.classifyInventory(type.name());

        int resultSlot = TransformationDecision.resultSlotFor(kind);
        ItemStack resultSlotItem = resultSlot >= 0 ? event.getInventory().getItem(resultSlot) : null;
        boolean resultSlotItemPresent = resultSlotItem != null && !resultSlotItem.getType().isAir();

        TransformationDecision.WriteDecision write = TransformationDecision.shouldWriteIdentity(
                cursorPresent, cursorIsAir, resultSlotItemPresent, kind);

        if (!write.shouldWrite()) {
            return;
        }

        if (write.writeToCursor()) {
            identityAdapter.writeIdentity(cursor, sourceId);
        } else {
            identityAdapter.writeIdentity(resultSlotItem, sourceId);
        }
    }

    private TrackedItemId readSourceIdentity(Inventory inventory, TransformationDecision.InventoryKind kind) {
        ItemStack firstInput = inventory.getItem(0);
        if (firstInput != null && !firstInput.getType().isAir()) {
            ItemIdentityAdapter.IdentityResult result = identityAdapter.readIdentity(firstInput);
            if (result.status() == ItemIdentityAdapter.IdentityStatus.EXISTING) {
                return result.itemId();
            }
        }

        if (kind == TransformationDecision.InventoryKind.ANVIL
                || kind == TransformationDecision.InventoryKind.SMITHING) {
            ItemStack secondInput = inventory.getItem(1);
            if (secondInput != null && !secondInput.getType().isAir()) {
                ItemIdentityAdapter.IdentityResult result = identityAdapter.readIdentity(secondInput);
                if (result.status() == ItemIdentityAdapter.IdentityStatus.EXISTING) {
                    return result.itemId();
                }
            }
        }

        return null;
    }
}
