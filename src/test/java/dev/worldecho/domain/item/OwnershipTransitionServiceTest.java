package dev.worldecho.domain.item;

import dev.worldecho.persistence.OwnershipLedgerRepository;
import dev.worldecho.persistence.TrackedItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipTransitionServiceTest {

    private FakeTrackedItemRepository trackedItemRepository;
    private FakeLedgerRepository ledgerRepository;
    private OwnershipTransitionService service;

    @BeforeEach
    void setUp() {
        trackedItemRepository = new FakeTrackedItemRepository();
        ledgerRepository = new FakeLedgerRepository();
        service = new OwnershipTransitionService(
                trackedItemRepository, ledgerRepository,
                Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneId.of("UTC"))
        );
    }

    @Test
    void transitionRecordsFirstEntry() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());

        OwnershipResult result = service.transition(itemId, subject,
                OwnershipTransitionReason.TRACKED, "key1", "admin", "");

        assertEquals(OwnershipResultStatus.RECORDED, result.status());
        assertTrue(result.optionalEntry().isPresent());
        assertEquals(1, result.entry().sequenceNumber());
        assertEquals(subject, result.entry().newSubject());
    }

    @Test
    void transitionRecordsSecondEntryWithPrevious() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID());
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID());

        service.transition(itemId, s1, OwnershipTransitionReason.TRACKED, "k1", "", "");
        OwnershipResult result = service.transition(itemId, s2,
                OwnershipTransitionReason.TRANSFERRED, "k2", "", "");

        assertEquals(OwnershipResultStatus.RECORDED, result.status());
        assertEquals(2, result.entry().sequenceNumber());
        assertTrue(result.entry().optionalPreviousSubject().isPresent());
        assertEquals(s1, result.entry().optionalPreviousSubject().get());
    }

    @Test
    void idempotentReplayReturnsExistingEntry() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());

        service.transition(itemId, subject, OwnershipTransitionReason.TRACKED, "same-key", "", "");
        OwnershipResult result = service.transition(itemId, subject,
                OwnershipTransitionReason.TRACKED, "same-key", "", "");

        assertEquals(OwnershipResultStatus.IDEMPOTENT_REPLAY, result.status());
        assertTrue(result.optionalEntry().isPresent());
    }

    @Test
    void idempotentKeyWithDifferentPayloadReturnsConflict() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID());
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID());

        service.transition(itemId, s1, OwnershipTransitionReason.TRACKED, "shared-key", "", "");
        OwnershipResult result = service.transition(itemId, s2,
                OwnershipTransitionReason.TRANSFERRED, "shared-key", "", "");

        assertEquals(OwnershipResultStatus.CONFLICT, result.status());
    }

    @Test
    void noChangeWhenSameSubject() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());

        service.transition(itemId, subject, OwnershipTransitionReason.TRACKED, "k1", "", "");
        OwnershipResult result = service.transition(itemId, subject,
                OwnershipTransitionReason.TRANSFERRED, "k2", "", "");

        assertEquals(OwnershipResultStatus.NO_CHANGE, result.status());
    }

    @Test
    void itemNotTrackedReturnsNotTracked() {
        TrackedItemId itemId = TrackedItemId.random();
        OwnershipResult result = service.transition(itemId,
                OwnershipSubject.player(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED, "k1", "", "");

        assertEquals(OwnershipResultStatus.ITEM_NOT_TRACKED, result.status());
    }

    @Test
    void invalidSubjectReturnsInvalid() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject invalid = new OwnershipSubject(OwnershipSubjectType.PLAYER, "not-a-uuid", "");

        OwnershipResult result = service.transition(itemId, invalid,
                OwnershipTransitionReason.TRACKED, "k1", "", "");

        assertEquals(OwnershipResultStatus.INVALID_SUBJECT, result.status());
    }

    @Test
    void currentOwnershipReturnsLatestState() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID());
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID());

        service.transition(itemId, s1, OwnershipTransitionReason.TRACKED, "k1", "", "");
        service.transition(itemId, s2, OwnershipTransitionReason.TRANSFERRED, "k2", "", "");

        Optional<OwnershipState> state = service.currentOwnership(itemId);
        assertTrue(state.isPresent());
        assertEquals(2, state.get().latestSequence());
        assertEquals(s2, state.get().currentSubject());
    }

    @Test
    void currentOwnershipReturnsEmptyForUnknownItem() {
        assertTrue(service.currentOwnership(TrackedItemId.random()).isEmpty());
    }

    @Test
    void emptyIdempotencyKeyDoesNotDeduplicate() {
        TrackedItemId itemId = trackedItemRepository.createTracked();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID());
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID());

        service.transition(itemId, s1, OwnershipTransitionReason.TRACKED, "", "", "");
        OwnershipResult result = service.transition(itemId, s2,
                OwnershipTransitionReason.TRANSFERRED, "", "", "");

        assertEquals(OwnershipResultStatus.RECORDED, result.status());
        assertEquals(2, result.entry().sequenceNumber());
    }

    private static class FakeTrackedItemRepository implements TrackedItemRepository {
        private final java.util.Set<TrackedItemId> existing = new java.util.HashSet<>();

        @Override
        public CreateResult create(TrackedItemRecord record) {
            return existing.add(record.itemId()) ? CreateResult.CREATED : CreateResult.ALREADY_EXISTS;
        }

        @Override
        public Optional<TrackedItemRecord> findById(TrackedItemId itemId) {
            return Optional.empty();
        }

        @Override
        public boolean exists(TrackedItemId itemId) {
            return existing.contains(itemId);
        }

        @Override
        public void observe(TrackedItemId itemId, long lastSeenEpochMilli) {
        }

        @Override
        public long count() {
            return existing.size();
        }

        TrackedItemId createTracked() {
            TrackedItemId id = TrackedItemId.random();
            existing.add(id);
            return id;
        }
    }

    private static class FakeLedgerRepository implements OwnershipLedgerRepository {
        private final java.util.Map<String, OwnershipLedgerEntry> byIdempotencyKey = new java.util.HashMap<>();
        private final java.util.List<OwnershipLedgerEntry> entries = new java.util.ArrayList<>();

        @Override
        public AppendResult append(OwnershipLedgerEntry entry) {
            if (entry.hasIdempotencyKey() && byIdempotencyKey.containsKey(entry.itemId() + ":" + entry.idempotencyKey())) {
                return AppendResult.IDEMPOTENT_REPLAY;
            }
            for (OwnershipLedgerEntry existing : entries) {
                if (existing.itemId().equals(entry.itemId())
                        && existing.sequenceNumber() == entry.sequenceNumber()) {
                    return AppendResult.CONFLICT;
                }
            }
            entries.add(entry);
            if (entry.hasIdempotencyKey()) {
                byIdempotencyKey.put(entry.itemId() + ":" + entry.idempotencyKey(), entry);
            }
            return AppendResult.APPENDED;
        }

        @Override
        public Optional<OwnershipState> findCurrentOwnership(TrackedItemId itemId) {
            OwnershipLedgerEntry latest = null;
            long count = 0;
            for (OwnershipLedgerEntry entry : entries) {
                if (entry.itemId().equals(itemId)) {
                    count++;
                    if (latest == null || entry.sequenceNumber() > latest.sequenceNumber()) {
                        latest = entry;
                    }
                }
            }
            if (latest == null) {
                return Optional.empty();
            }
            return Optional.of(new OwnershipState(
                    itemId, latest.newSubject(), latest.sequenceNumber(),
                    latest.transitionReason(), latest.occurredAt(), count
            ));
        }

        @Override
        public List<OwnershipLedgerEntry> findHistory(TrackedItemId itemId, int limit) {
            return entries.stream()
                    .filter(e -> e.itemId().equals(itemId))
                    .sorted((a, b) -> Integer.compare(b.sequenceNumber(), a.sequenceNumber()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countHistory(TrackedItemId itemId) {
            return entries.stream().filter(e -> e.itemId().equals(itemId)).count();
        }

        @Override
        public Optional<OwnershipLedgerEntry> findByIdempotencyKey(TrackedItemId itemId, String idempotencyKey) {
            if (idempotencyKey == null || idempotencyKey.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(byIdempotencyKey.get(itemId + ":" + idempotencyKey));
        }

        @Override
        public long count() {
            return entries.size();
        }
    }
}
