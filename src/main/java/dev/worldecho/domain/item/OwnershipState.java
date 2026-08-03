package dev.worldecho.domain.item;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable projection of the current ownership state for a tracked item.
 *
 * <p>Derived from the latest ledger entry — the ledger remains authoritative.
 */
public record OwnershipState(
        TrackedItemId itemId,
        OwnershipSubject currentSubject,
        int latestSequence,
        OwnershipTransitionReason latestTransitionReason,
        Instant lastChangedAt,
        long historyCount
) {

    public OwnershipState {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(latestTransitionReason, "latestTransitionReason");
        Objects.requireNonNull(lastChangedAt, "lastChangedAt");
    }

    public Optional<OwnershipSubject> optionalCurrentSubject() {
        return currentSubject == null ? Optional.empty() : Optional.of(currentSubject);
    }
}
