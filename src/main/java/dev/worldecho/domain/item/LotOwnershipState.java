package dev.worldecho.domain.item;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Current ownership projection for a tracked item lot.
 */
public record LotOwnershipState(
        TrackedItemLotId lotId,
        OwnershipSubject currentSubject,
        int latestSequence,
        OwnershipTransitionReason latestReason,
        Instant occurredAt,
        long historyCount
) {

    public LotOwnershipState {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(currentSubject, "currentSubject");
        Objects.requireNonNull(latestReason, "latestReason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public Optional<OwnershipSubject> optionalCurrentSubject() {
        return Optional.of(currentSubject);
    }
}
