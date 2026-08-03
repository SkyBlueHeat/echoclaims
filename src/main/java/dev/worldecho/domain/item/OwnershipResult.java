package dev.worldecho.domain.item;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of an ownership transition attempt.
 */
public record OwnershipResult(
        OwnershipResultStatus status,
        OwnershipLedgerEntry entry,
        TrackedItemId itemId,
        String diagnostic,
        List<OwnershipLedgerEntry> history
) {

    public OwnershipResult {
        Objects.requireNonNull(status, "status");
        entry = entry == null ? null : entry;
        itemId = itemId == null ? null : itemId;
        diagnostic = diagnostic == null ? "" : diagnostic;
        history = history == null ? List.of() : List.copyOf(history);
    }

    public static OwnershipResult recorded(OwnershipLedgerEntry entry) {
        return new OwnershipResult(OwnershipResultStatus.RECORDED, entry, entry.itemId(), "", List.of());
    }

    public static OwnershipResult idempotentReplay(OwnershipLedgerEntry existing) {
        return new OwnershipResult(OwnershipResultStatus.IDEMPOTENT_REPLAY, existing, existing.itemId(), "", List.of());
    }

    public static OwnershipResult noChange(TrackedItemId itemId, OwnershipSubject currentSubject) {
        return new OwnershipResult(OwnershipResultStatus.NO_CHANGE, null, itemId,
                "Subject unchanged: " + currentSubject.describe(), List.of());
    }

    public static OwnershipResult itemNotTracked(TrackedItemId itemId) {
        return new OwnershipResult(OwnershipResultStatus.ITEM_NOT_TRACKED, null, itemId,
                "Item not tracked: " + (itemId == null ? "?" : itemId), List.of());
    }

    public static OwnershipResult invalidSubject(String reason) {
        return new OwnershipResult(OwnershipResultStatus.INVALID_SUBJECT, null, null, reason, List.of());
    }

    public static OwnershipResult conflict(String reason) {
        return new OwnershipResult(OwnershipResultStatus.CONFLICT, null, null, reason, List.of());
    }

    public static OwnershipResult persistenceFailure(String reason) {
        return new OwnershipResult(OwnershipResultStatus.PERSISTENCE_FAILURE, null, null, reason, List.of());
    }

    public Optional<OwnershipLedgerEntry> optionalEntry() {
        return Optional.ofNullable(entry);
    }

    public Optional<TrackedItemId> optionalItemId() {
        return Optional.ofNullable(itemId);
    }

    public Optional<String> optionalDiagnostic() {
        return diagnostic.isEmpty() ? Optional.empty() : Optional.of(diagnostic);
    }
}
