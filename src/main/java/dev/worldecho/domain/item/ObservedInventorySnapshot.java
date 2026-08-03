package dev.worldecho.domain.item;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of an entire player inventory observation.
 *
 * <p>Captured on the server thread from a final inventory state.
 * Contains only immutable data safe for asynchronous processing.
 */
public record ObservedInventorySnapshot(
        UUID playerUuid,
        String playerDisplayName,
        List<ObservedInventorySlot> slots,
        ReconciliationCycle cycle
) {

    public ObservedInventorySnapshot {
        Objects.requireNonNull(playerUuid, "playerUuid");
        playerDisplayName = playerDisplayName == null ? "" : playerDisplayName.strip();
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public int nonEmptySlotCount() {
        return (int) slots.stream().filter(slot -> !slot.isEmpty()).count();
    }

    public int uniqueCount() {
        return (int) slots.stream().filter(ObservedInventorySlot::isUnique).count();
    }

    public int lotCount() {
        return (int) slots.stream().filter(ObservedInventorySlot::isLot).count();
    }

    public int malformedCount() {
        return (int) slots.stream().filter(ObservedInventorySlot::malformedIdentity).count();
    }
}
