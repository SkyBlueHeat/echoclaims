package dev.worldecho.domain.item;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable append-only entry in the ownership ledger for a tracked item.
 */
public record OwnershipLedgerEntry(
        String entryId,
        TrackedItemId itemId,
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

    public OwnershipLedgerEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(itemId, "itemId");
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

    public Optional<String> optionalStoryEventId() {
        return storyEventId.isEmpty() ? Optional.empty() : Optional.of(storyEventId);
    }

    public Optional<String> optionalNotes() {
        return notes.isEmpty() ? Optional.empty() : Optional.of(notes);
    }

    public boolean hasIdempotencyKey() {
        return !idempotencyKey.isEmpty();
    }
}
