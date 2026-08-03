package dev.worldecho.domain.item;

import dev.worldecho.persistence.OwnershipLedgerRepository;
import dev.worldecho.persistence.TrackedItemRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure-Java service that validates and records ownership transitions.
 *
 * <p>Does not access Bukkit.  Persistence errors are caught and converted to
 * {@link OwnershipResultStatus#PERSISTENCE_FAILURE} results.
 */
public final class OwnershipTransitionService {

    private final TrackedItemRepository trackedItemRepository;
    private final OwnershipLedgerRepository ledgerRepository;
    private final Clock clock;

    public OwnershipTransitionService(
            TrackedItemRepository trackedItemRepository,
            OwnershipLedgerRepository ledgerRepository,
            Clock clock
    ) {
        this.trackedItemRepository = Objects.requireNonNull(trackedItemRepository, "trackedItemRepository");
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository, "ledgerRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OwnershipResult transition(
            TrackedItemId itemId,
            OwnershipSubject newSubject,
            OwnershipTransitionReason reason,
            String idempotencyKey,
            String source,
            String notes
    ) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(newSubject, "newSubject");
        Objects.requireNonNull(reason, "reason");

        if (!newSubject.isValid()) {
            return OwnershipResult.invalidSubject("Invalid subject: " + newSubject.describe());
        }

        try {
            if (!trackedItemRepository.exists(itemId)) {
                return OwnershipResult.itemNotTracked(itemId);
            }

            String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
            if (!normalizedKey.isEmpty()) {
                Optional<OwnershipLedgerEntry> existing =
                        ledgerRepository.findByIdempotencyKey(itemId, normalizedKey);
                if (existing.isPresent()) {
                    OwnershipLedgerEntry prior = existing.get();
                    if (matchesRequest(prior, newSubject, reason)) {
                        return OwnershipResult.idempotentReplay(prior);
                    }
                    return OwnershipResult.conflict(
                            "Idempotency key '" + normalizedKey
                                    + "' already used with different payload for item " + itemId);
                }
            }

            Optional<OwnershipState> currentState = ledgerRepository.findCurrentOwnership(itemId);
            OwnershipSubject previousSubject = currentState
                    .flatMap(OwnershipState::optionalCurrentSubject)
                    .orElse(null);
            int nextSequence = currentState.map(OwnershipState::latestSequence).orElse(0) + 1;

            if (previousSubject != null && previousSubject.equals(newSubject)) {
                return OwnershipResult.noChange(itemId, newSubject);
            }

            Instant now = Instant.now(clock);
            OwnershipLedgerEntry entry = new OwnershipLedgerEntry(
                    UUID.randomUUID().toString(),
                    itemId,
                    nextSequence,
                    previousSubject,
                    newSubject,
                    reason,
                    now,
                    now,
                    source == null ? "" : source,
                    "",
                    normalizedKey,
                    notes == null ? "" : notes
            );

            OwnershipLedgerRepository.AppendResult appendResult = ledgerRepository.append(entry);
            return switch (appendResult) {
                case APPENDED -> OwnershipResult.recorded(entry);
                case IDEMPOTENT_REPLAY -> {
                    Optional<OwnershipLedgerEntry> existing = ledgerRepository.findByIdempotencyKey(itemId, normalizedKey);
                    yield existing.isPresent()
                            ? OwnershipResult.idempotentReplay(existing.get())
                            : OwnershipResult.recorded(entry);
                }
                case CONFLICT -> OwnershipResult.conflict(
                        "Concurrent append conflict for item " + itemId);
            };
        } catch (Exception exception) {
            return OwnershipResult.persistenceFailure(
                    "Persistence error: " + exception.getMessage());
        }
    }

    public Optional<OwnershipState> currentOwnership(TrackedItemId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        try {
            return ledgerRepository.findCurrentOwnership(itemId);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static boolean matchesRequest(
            OwnershipLedgerEntry entry,
            OwnershipSubject newSubject,
            OwnershipTransitionReason reason
    ) {
        return entry.newSubject().equals(newSubject)
                && entry.transitionReason() == reason;
    }
}
