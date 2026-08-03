package dev.worldecho.domain.item;

import dev.worldecho.persistence.LotOwnershipLedgerRepository;
import dev.worldecho.persistence.TrackedItemLotRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure-Java service that validates and records lot ownership transitions.
 */
public final class LotOwnershipTransitionService {

    private final TrackedItemLotRepository lotRepository;
    private final LotOwnershipLedgerRepository ledgerRepository;
    private final Clock clock;

    public LotOwnershipTransitionService(
            TrackedItemLotRepository lotRepository,
            LotOwnershipLedgerRepository ledgerRepository,
            Clock clock
    ) {
        this.lotRepository = Objects.requireNonNull(lotRepository, "lotRepository");
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository, "ledgerRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OwnershipResult transition(
            TrackedItemLotId lotId,
            OwnershipSubject newSubject,
            OwnershipTransitionReason reason,
            String idempotencyKey,
            String source,
            String notes
    ) {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(newSubject, "newSubject");
        Objects.requireNonNull(reason, "reason");

        if (!newSubject.isValid()) {
            return OwnershipResult.invalidSubject("Invalid subject: " + newSubject.describe());
        }

        try {
            if (!lotRepository.exists(lotId)) {
                return OwnershipResult.itemNotTracked(TrackedItemId.parse(lotId.toString()));
            }

            String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
            if (!normalizedKey.isEmpty()) {
                Optional<LotOwnershipLedgerEntry> existing =
                        ledgerRepository.findByIdempotencyKey(lotId, normalizedKey);
                if (existing.isPresent()) {
                    LotOwnershipLedgerEntry prior = existing.get();
                    if (matchesRequest(prior, newSubject, reason)) {
                        return OwnershipResult.idempotentReplay(mapToItemLedger(prior));
                    }
                    return OwnershipResult.conflict(
                            "Idempotency key '" + normalizedKey
                                    + "' already used with different payload for lot " + lotId);
                }
            }

            Optional<LotOwnershipState> currentState = ledgerRepository.findCurrentOwnership(lotId);
            OwnershipSubject previousSubject = currentState
                    .flatMap(LotOwnershipState::optionalCurrentSubject)
                    .orElse(null);
            int nextSequence = currentState.map(LotOwnershipState::latestSequence).orElse(0) + 1;

            if (previousSubject != null && previousSubject.equals(newSubject)) {
                return OwnershipResult.noChange(TrackedItemId.parse(lotId.toString()), newSubject);
            }

            Instant now = Instant.now(clock);
            LotOwnershipLedgerEntry entry = new LotOwnershipLedgerEntry(
                    UUID.randomUUID().toString(),
                    lotId,
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

            LotOwnershipLedgerRepository.AppendResult appendResult = ledgerRepository.append(entry);
            return switch (appendResult) {
                case APPENDED -> OwnershipResult.recorded(mapToItemLedger(entry));
                case IDEMPOTENT_REPLAY -> {
                    Optional<LotOwnershipLedgerEntry> existing =
                            ledgerRepository.findByIdempotencyKey(lotId, normalizedKey);
                    yield existing.isPresent()
                            ? OwnershipResult.idempotentReplay(mapToItemLedger(existing.get()))
                            : OwnershipResult.recorded(mapToItemLedger(entry));
                }
                case CONFLICT -> OwnershipResult.conflict(
                        "Concurrent append conflict for lot " + lotId);
            };
        } catch (Exception exception) {
            return OwnershipResult.persistenceFailure(
                    "Persistence error: " + exception.getMessage());
        }
    }

    public Optional<LotOwnershipState> currentOwnership(TrackedItemLotId lotId) {
        Objects.requireNonNull(lotId, "lotId");
        try {
            return ledgerRepository.findCurrentOwnership(lotId);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static boolean matchesRequest(
            LotOwnershipLedgerEntry entry,
            OwnershipSubject newSubject,
            OwnershipTransitionReason reason
    ) {
        return entry.newSubject().equals(newSubject)
                && entry.transitionReason() == reason;
    }

    private static OwnershipLedgerEntry mapToItemLedger(LotOwnershipLedgerEntry lotEntry) {
        return new OwnershipLedgerEntry(
                lotEntry.entryId(),
                TrackedItemId.parse(lotEntry.lotId().toString()),
                lotEntry.sequenceNumber(),
                lotEntry.previousSubject(),
                lotEntry.newSubject(),
                lotEntry.transitionReason(),
                lotEntry.occurredAt(),
                lotEntry.recordedAt(),
                lotEntry.source(),
                lotEntry.storyEventId(),
                lotEntry.idempotencyKey(),
                lotEntry.notes()
        );
    }
}
