package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformationDecisionTest {

    // --- Inventory classification ---

    @Test
    void anvilIsClassifiedAsAnvil() {
        assertEquals(TransformationDecision.InventoryKind.ANVIL,
                TransformationDecision.classifyInventory("ANVIL"));
    }

    @Test
    void smithingIsClassifiedAsSmithing() {
        assertEquals(TransformationDecision.InventoryKind.SMITHING,
                TransformationDecision.classifyInventory("SMITHING"));
    }

    @Test
    void grindstoneIsClassifiedAsGrindstone() {
        assertEquals(TransformationDecision.InventoryKind.GRINDSTONE,
                TransformationDecision.classifyInventory("GRINDSTONE"));
    }

    @Test
    void craftingTableIsNotTransformation() {
        assertEquals(TransformationDecision.InventoryKind.OTHER,
                TransformationDecision.classifyInventory("CRAFTING"));
    }

    @Test
    void workbenchIsNotTransformation() {
        assertEquals(TransformationDecision.InventoryKind.OTHER,
                TransformationDecision.classifyInventory("WORKBENCH"));
    }

    // --- Result slot ---

    @Test
    void anvilResultSlotIs2() {
        assertEquals(2, TransformationDecision.resultSlotFor(TransformationDecision.InventoryKind.ANVIL));
    }

    @Test
    void grindstoneResultSlotIs2() {
        assertEquals(2, TransformationDecision.resultSlotFor(TransformationDecision.InventoryKind.GRINDSTONE));
    }

    @Test
    void smithingResultSlotIs3() {
        assertEquals(3, TransformationDecision.resultSlotFor(TransformationDecision.InventoryKind.SMITHING));
    }

    @Test
    void otherResultSlotIsNegativeOne() {
        assertEquals(-1, TransformationDecision.resultSlotFor(TransformationDecision.InventoryKind.OTHER));
    }

    // --- Capture decisions ---

    @Test
    void anvilClickOnResultSlotCapturesIdentity() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("ANVIL", "PICKUP_ALL", 2);
        assertTrue(decision.shouldCapture());
        assertEquals(TransformationDecision.InventoryKind.ANVIL, decision.inventoryKind());
        assertEquals(2, decision.resultSlot());
    }

    @Test
    void smithingClickOnResultSlotCapturesIdentity() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("SMITHING", "PICKUP_ALL", 3);
        assertTrue(decision.shouldCapture());
        assertEquals(TransformationDecision.InventoryKind.SMITHING, decision.inventoryKind());
        assertEquals(3, decision.resultSlot());
    }

    @Test
    void grindstoneClickOnResultSlotCapturesIdentity() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("GRINDSTONE", "PICKUP_ALL", 2);
        assertTrue(decision.shouldCapture());
        assertEquals(TransformationDecision.InventoryKind.GRINDSTONE, decision.inventoryKind());
    }

    @Test
    void anvilClickOnInputSlotDoesNotCapture() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("ANVIL", "PICKUP_ALL", 0);
        assertFalse(decision.shouldCapture());
    }

    @Test
    void nonTransformationInventoryDoesNotCapture() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("CRAFTING", "PICKUP_ALL", 0);
        assertFalse(decision.shouldCapture());
    }

    @Test
    void irrelevantActionDoesNotCapture() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("ANVIL", "PLACE_SOME", 2);
        assertFalse(decision.shouldCapture());
    }

    @Test
    void moveToOtherInventoryCapturesOnAnvilResult() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("ANVIL", "MOVE_TO_OTHER_INVENTORY", 2);
        assertTrue(decision.shouldCapture());
    }

    @Test
    void collectToCursorCapturesOnAnvilResult() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("ANVIL", "COLLECT_TO_CURSOR", 2);
        assertTrue(decision.shouldCapture());
    }

    @Test
    void swapWithCursorCapturesOnAnvilResult() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("ANVIL", "SWAP_WITH_CURSOR", 2);
        assertTrue(decision.shouldCapture());
    }

    @Test
    void pickupHalfCapturesOnAnvilResult() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("ANVIL", "PICKUP_HALF", 2);
        assertTrue(decision.shouldCapture());
    }

    // --- Write decisions ---

    @Test
    void writeIdentityToCursorWhenCursorHasItem() {
        TransformationDecision.WriteDecision decision =
                TransformationDecision.shouldWriteIdentity(true, false, true,
                        TransformationDecision.InventoryKind.ANVIL);
        assertTrue(decision.shouldWrite());
        assertTrue(decision.writeToCursor());
    }

    @Test
    void writeIdentityToResultSlotWhenCursorIsEmpty() {
        TransformationDecision.WriteDecision decision =
                TransformationDecision.shouldWriteIdentity(false, true, true,
                        TransformationDecision.InventoryKind.ANVIL);
        assertTrue(decision.shouldWrite());
        assertFalse(decision.writeToCursor());
        assertEquals(2, decision.resultSlot());
    }

    @Test
    void writeIdentityToSmithingResultSlotWhenCursorIsEmpty() {
        TransformationDecision.WriteDecision decision =
                TransformationDecision.shouldWriteIdentity(false, true, true,
                        TransformationDecision.InventoryKind.SMITHING);
        assertTrue(decision.shouldWrite());
        assertEquals(3, decision.resultSlot());
    }

    @Test
    void skipWriteWhenNeitherCursorNorResultHasItem() {
        TransformationDecision.WriteDecision decision =
                TransformationDecision.shouldWriteIdentity(false, true, false,
                        TransformationDecision.InventoryKind.ANVIL);
        assertFalse(decision.shouldWrite());
    }

    @Test
    void skipWriteForNonTransformationInventory() {
        TransformationDecision.WriteDecision decision =
                TransformationDecision.shouldWriteIdentity(true, false, true,
                        TransformationDecision.InventoryKind.OTHER);
        assertFalse(decision.shouldWrite());
    }

    // --- Feature gate ---

    @Test
    void featureEnabledWhenAllSettingsTrue() {
        assertTrue(TransformationDecision.isFeatureEnabled(true, true, true));
    }

    @Test
    void featureDisabledWhenTrackingDisabled() {
        assertFalse(TransformationDecision.isFeatureEnabled(false, true, true));
    }

    @Test
    void featureDisabledWhenPlayerInventoriesDisabled() {
        assertFalse(TransformationDecision.isFeatureEnabled(true, false, true));
    }

    @Test
    void featureDisabledWhenTransformContinuityDisabled() {
        assertFalse(TransformationDecision.isFeatureEnabled(true, true, false));
    }

    // --- Crafting result gets new identity (not preserved) ---

    @Test
    void craftingTableDoesNotCaptureIdentity() {
        // Crafting table results should NOT inherit source identity — they get a new identity
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("CRAFTING", "PICKUP_ALL", 0);
        assertFalse(decision.shouldCapture());
    }

    @Test
    void stonecutterDoesNotCaptureIdentity() {
        TransformationDecision.CaptureDecision decision =
                TransformationDecision.shouldCaptureIdentity("STONECUTTER", "PICKUP_ALL", 1);
        assertFalse(decision.shouldCapture());
    }
}
