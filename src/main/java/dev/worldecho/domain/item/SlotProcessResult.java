package dev.worldecho.domain.item;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of processing a single inventory slot during reconciliation.
 */
public record SlotProcessResult(
        ObservedInventorySlot slot,
        Status status,
        OwnershipResult ownershipResult,
        String errorMessage
) {

    public enum Status {
        PROCESSED,
        ASSIGNED,
        MALFORMED,
        PERSISTENCE_FAILURE,
        SKIPPED
    }

    public SlotProcessResult {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(status, "status");
    }

    public static SlotProcessResult processed(ObservedInventorySlot slot, OwnershipResult result) {
        return new SlotProcessResult(slot, Status.PROCESSED, result, null);
    }

    public static SlotProcessResult assigned(ObservedInventorySlot slot) {
        return new SlotProcessResult(slot, Status.ASSIGNED, null, null);
    }

    public static SlotProcessResult malformed(ObservedInventorySlot slot) {
        return new SlotProcessResult(slot, Status.MALFORMED, null, null);
    }

    public static SlotProcessResult persistenceFailure(ObservedInventorySlot slot, String error) {
        return new SlotProcessResult(slot, Status.PERSISTENCE_FAILURE, null, error);
    }

    public static SlotProcessResult skipped(ObservedInventorySlot slot) {
        return new SlotProcessResult(slot, Status.SKIPPED, null, null);
    }

    public Optional<OwnershipResult> optionalOwnershipResult() {
        return Optional.ofNullable(ownershipResult);
    }
}
