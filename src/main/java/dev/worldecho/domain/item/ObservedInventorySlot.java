package dev.worldecho.domain.item;

import dev.worldecho.domain.content.ContentKey;

import java.util.Optional;

/**
 * Immutable snapshot of a single inventory slot observation.
 *
 * <p>Captured on the server thread.  Safe to hand to asynchronous processing.
 */
public record ObservedInventorySlot(
        String inventorySection,
        int slotIndex,
        String material,
        int amount,
        ContentKey contentKey,
        String providerId,
        ItemDescriptor descriptor,
        IdentityClassificationResult classification,
        TrackedItemId existingUniqueId,
        TrackedItemLotId existingLotId,
        LotCompatibilityFingerprint lotFingerprint,
        boolean malformedIdentity
) {

    public ObservedInventorySlot {
        inventorySection = inventorySection == null ? "" : inventorySection.strip();
        material = material == null ? "" : material.strip();
        providerId = providerId == null ? "" : providerId.strip();
    }

    public boolean isEmpty() {
        return material.isEmpty() || amount <= 0;
    }

    public boolean isUnique() {
        return classification != null && classification.isUnique();
    }

    public boolean isLot() {
        return classification != null && classification.isLot();
    }

    public Optional<TrackedItemId> optionalExistingUniqueId() {
        return Optional.ofNullable(existingUniqueId);
    }

    public Optional<TrackedItemLotId> optionalExistingLotId() {
        return Optional.ofNullable(existingLotId);
    }
}
