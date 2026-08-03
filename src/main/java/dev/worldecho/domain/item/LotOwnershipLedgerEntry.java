package dev.worldecho.domain.item;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable append-only entry in the lot ownership ledger.
 */
public record LotOwnershipLedgerEntry(
        String entryId,
        TrackedItemLotId lotId,
        int sequenceNumber,
        OwnershipSubject previousSubject,
        OwnershipSubject newSubject,
        OwnershipTransitionReason transitionReason,
        Instant occurredAt,
        Instant recordedAt,
        String source,
        String storyEventId,
        String idempotencyKey,
        String notes
) {

    public LotOwnershipLedgerEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(lotId, "lotId");
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        Objects.requireNonNull(newSubject, "newSubject");
        Objects.requireNonNull(transitionReason, "transitionReason");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        source = source == null ? "" : source.strip();
        storyEventId = storyEventId == null ? "" : storyEventId.strip();
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.strip();
        notes = notes == null ? "" : notes.strip();
    }

    public Optional<OwnershipSubject> optionalPreviousSubject() {
        return previousSubject == null ? Optional.empty() : Optional.of(previousSubject);
    }
}
