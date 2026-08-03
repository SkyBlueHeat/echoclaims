package dev.worldecho.domain.item;

import java.util.Objects;

/**
 * Pure-Java, testable decision component for item transformation identity continuity.
 *
 * <p>Encapsulates the decision logic that was previously embedded inside the
 * Bukkit-dependent {@code ItemTransformationListener}.  The listener delegates
 * to this class so that the following decisions are unit-testable without Bukkit:
 *
 * <ul>
 *   <li>Whether an inventory type is a transformation inventory (anvil, smithing, grindstone)</li>
 *   <li>Which slot contains the transformation result</li>
 *   <li>Whether a click action should trigger identity capture</li>
 *   <li>Whether to write identity to cursor or result slot</li>
 * </ul>
 */
public final class TransformationDecision {

    public enum InventoryKind {
        ANVIL,
        SMITHING,
        GRINDSTONE,
        OTHER
    }

    public enum ClickAction {
        PICKUP_ALL,
        PICKUP_HALF,
        MOVE_TO_OTHER_INVENTORY,
        COLLECT_TO_CURSOR,
        SWAP_WITH_CURSOR,
        OTHER
    }

    public record CaptureDecision(
            boolean shouldCapture,
            InventoryKind inventoryKind,
            int resultSlot
    ) {
        public static CaptureDecision skip() {
            return new CaptureDecision(false, InventoryKind.OTHER, -1);
        }

        public static CaptureDecision capture(InventoryKind kind, int resultSlot) {
            return new CaptureDecision(true, kind, resultSlot);
        }
    }

    public record WriteDecision(
            boolean shouldWrite,
            boolean writeToCursor,
            int resultSlot
    ) {
        public static WriteDecision skip() {
            return new WriteDecision(false, false, -1);
        }

        public static WriteDecision toCursor() {
            return new WriteDecision(true, true, -1);
        }

        public static WriteDecision toResultSlot(int slot) {
            return new WriteDecision(true, false, slot);
        }
    }

    private TransformationDecision() {
    }

    /**
     * Classifies an inventory type string into a transformation-relevant kind.
     *
     * @param inventoryType the Bukkit {@link org.bukkit.event.inventory.InventoryType} name
     * @return the classified kind, or {@link InventoryKind#OTHER} if not a transformation inventory
     */
    public static InventoryKind classifyInventory(String inventoryType) {
        Objects.requireNonNull(inventoryType, "inventoryType");
        return switch (inventoryType.toUpperCase(java.util.Locale.ROOT)) {
            case "ANVIL" -> InventoryKind.ANVIL;
            case "SMITHING" -> InventoryKind.SMITHING;
            case "GRINDSTONE" -> InventoryKind.GRINDSTONE;
            default -> InventoryKind.OTHER;
        };
    }

    /**
     * Determines the result slot index for a transformation inventory.
     *
     * @param kind the inventory kind (must be a transformation inventory)
     * @return the result slot index, or -1 if not a transformation inventory
     */
    public static int resultSlotFor(InventoryKind kind) {
        return switch (kind) {
            case ANVIL, GRINDSTONE -> 2;
            case SMITHING -> 3;
            case OTHER -> -1;
        };
    }

    /**
     * Classifies a click action string into a transformation-relevant action.
     *
     * @param actionName the Bukkit {@link org.bukkit.event.inventory.InventoryAction} name
     * @return the classified action
     */
    public static ClickAction classifyAction(String actionName) {
        Objects.requireNonNull(actionName, "actionName");
        return switch (actionName.toUpperCase(java.util.Locale.ROOT)) {
            case "PICKUP_ALL" -> ClickAction.PICKUP_ALL;
            case "PICKUP_HALF" -> ClickAction.PICKUP_HALF;
            case "MOVE_TO_OTHER_INVENTORY" -> ClickAction.MOVE_TO_OTHER_INVENTORY;
            case "COLLECT_TO_CURSOR" -> ClickAction.COLLECT_TO_CURSOR;
            case "SWAP_WITH_CURSOR" -> ClickAction.SWAP_WITH_CURSOR;
            default -> ClickAction.OTHER;
        };
    }

    /**
     * Decides whether to capture identity before a transformation click.
     *
     * @param inventoryType the inventory type name
     * @param actionName the click action name
     * @param rawSlot the raw slot that was clicked
     * @return a capture decision
     */
    public static CaptureDecision shouldCaptureIdentity(
            String inventoryType,
            String actionName,
            int rawSlot
    ) {
        InventoryKind kind = classifyInventory(inventoryType);
        if (kind == InventoryKind.OTHER) {
            return CaptureDecision.skip();
        }

        ClickAction action = classifyAction(actionName);
        if (action == ClickAction.OTHER) {
            return CaptureDecision.skip();
        }

        int resultSlot = resultSlotFor(kind);
        if (rawSlot != resultSlot) {
            return CaptureDecision.skip();
        }

        return CaptureDecision.capture(kind, resultSlot);
    }

    /**
     * Decides whether to write identity to the result of a transformation.
     *
     * @param cursorPresent whether the cursor has an item after the click
     * @param cursorIsAir whether the cursor item is air
     * @param resultSlotItemPresent whether the result slot has a non-air item
     * @param inventoryKind the kind of transformation inventory
     * @return a write decision
     */
    public static WriteDecision shouldWriteIdentity(
            boolean cursorPresent,
            boolean cursorIsAir,
            boolean resultSlotItemPresent,
            InventoryKind inventoryKind
    ) {
        if (inventoryKind == InventoryKind.OTHER) {
            return WriteDecision.skip();
        }

        if (cursorPresent && !cursorIsAir) {
            return WriteDecision.toCursor();
        }

        if (resultSlotItemPresent) {
            int slot = resultSlotFor(inventoryKind);
            return WriteDecision.toResultSlot(slot);
        }

        return WriteDecision.skip();
    }

    /**
     * Decides whether the transformation feature should be active given the settings.
     *
     * @param automaticTrackingEnabled whether automatic tracking is enabled
     * @param playerInventories whether player inventory tracking is enabled
     * @param transformIdentityContinuity whether transformation identity continuity is enabled
     * @return true if the transformation listener should process events
     */
    public static boolean isFeatureEnabled(
            boolean automaticTrackingEnabled,
            boolean playerInventories,
            boolean transformIdentityContinuity
    ) {
        return automaticTrackingEnabled && playerInventories && transformIdentityContinuity;
    }
}
