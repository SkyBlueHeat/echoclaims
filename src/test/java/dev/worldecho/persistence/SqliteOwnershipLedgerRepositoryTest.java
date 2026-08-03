package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.item.OwnershipLedgerEntry;
import dev.worldecho.domain.item.OwnershipState;
import dev.worldecho.domain.item.OwnershipSubject;
import dev.worldecho.domain.item.OwnershipTransitionReason;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TrackedItemRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteOwnershipLedgerRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private TrackedItemRepository trackedItemRepository;
    private OwnershipLedgerRepository ledgerRepository;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir.resolve("ledger-test.db"));
        database.initialize();
        trackedItemRepository = new SqliteTrackedItemRepository(database);
        ledgerRepository = new SqliteOwnershipLedgerRepository(database);
    }

    @Test
    void appendAndFindCurrentOwnership() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID(), "Steve");
        OwnershipLedgerEntry entry = entry(itemId, 1, null, subject, OwnershipTransitionReason.TRACKED, "key1");

        assertEquals(OwnershipLedgerRepository.AppendResult.APPENDED, ledgerRepository.append(entry));

        Optional<OwnershipState> state = ledgerRepository.findCurrentOwnership(itemId);
        assertTrue(state.isPresent());
        assertEquals(1, state.get().latestSequence());
        assertEquals(subject, state.get().currentSubject());
        assertEquals(1L, state.get().historyCount());
    }

    @Test
    void appendMultipleEntriesAndFindLatest() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID(), "Steve");
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID(), "Zombie");

        ledgerRepository.append(entry(itemId, 1, null, s1, OwnershipTransitionReason.TRACKED, "k1"));
        ledgerRepository.append(entry(itemId, 2, s1, s2, OwnershipTransitionReason.TRANSFERRED, "k2"));

        Optional<OwnershipState> state = ledgerRepository.findCurrentOwnership(itemId);
        assertTrue(state.isPresent());
        assertEquals(2, state.get().latestSequence());
        assertEquals(s2, state.get().currentSubject());
        assertEquals(2L, state.get().historyCount());
    }

    @Test
    void idempotentReplayOnDuplicateIdempotencyKey() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());
        OwnershipLedgerEntry entry = entry(itemId, 1, null, subject, OwnershipTransitionReason.TRACKED, "same-key");

        assertEquals(OwnershipLedgerRepository.AppendResult.APPENDED, ledgerRepository.append(entry));
        assertEquals(OwnershipLedgerRepository.AppendResult.IDEMPOTENT_REPLAY, ledgerRepository.append(entry));
    }

    @Test
    void conflictOnDuplicateSequenceNumber() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID());
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID());

        ledgerRepository.append(entry(itemId, 1, null, s1, OwnershipTransitionReason.TRACKED, "k1"));
        OwnershipLedgerEntry dup = entry(itemId, 1, null, s2, OwnershipTransitionReason.TRANSFERRED, "k2");
        assertEquals(OwnershipLedgerRepository.AppendResult.CONFLICT, ledgerRepository.append(dup));
    }

    @Test
    void findHistoryReturnsEntriesInDescendingOrder() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID());
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID());
        OwnershipSubject s3 = OwnershipSubject.system("loot-system");

        ledgerRepository.append(entry(itemId, 1, null, s1, OwnershipTransitionReason.TRACKED, "k1"));
        ledgerRepository.append(entry(itemId, 2, s1, s2, OwnershipTransitionReason.TRANSFERRED, "k2"));
        ledgerRepository.append(entry(itemId, 3, s2, s3, OwnershipTransitionReason.ADMIN_ASSIGNMENT, "k3"));

        List<OwnershipLedgerEntry> history = ledgerRepository.findHistory(itemId, 10);
        assertEquals(3, history.size());
        assertEquals(3, history.get(0).sequenceNumber());
        assertEquals(2, history.get(1).sequenceNumber());
        assertEquals(1, history.get(2).sequenceNumber());
    }

    @Test
    void findHistoryRespectsLimit() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        for (int i = 1; i <= 5; i++) {
            ledgerRepository.append(entry(itemId, i,
                    i == 1 ? null : OwnershipSubject.system("prev"),
                    OwnershipSubject.system("owner-" + i),
                    OwnershipTransitionReason.TRANSFERRED, "k" + i));
        }

        List<OwnershipLedgerEntry> history = ledgerRepository.findHistory(itemId, 3);
        assertEquals(3, history.size());
        assertEquals(5, history.get(0).sequenceNumber());
    }

    @Test
    void countHistoryReturnsCorrectCount() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        ledgerRepository.append(entry(itemId, 1, null, OwnershipSubject.player(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED, "k1"));
        ledgerRepository.append(entry(itemId, 2, OwnershipSubject.player(UUID.randomUUID()),
                OwnershipSubject.entity(UUID.randomUUID()), OwnershipTransitionReason.TRANSFERRED, "k2"));

        assertEquals(2L, ledgerRepository.countHistory(itemId));
    }

    @Test
    void findByIdempotencyKeyReturnsEntry() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());
        ledgerRepository.append(entry(itemId, 1, null, subject, OwnershipTransitionReason.TRACKED, "unique-key"));

        Optional<OwnershipLedgerEntry> found = ledgerRepository.findByIdempotencyKey(itemId, "unique-key");
        assertTrue(found.isPresent());
        assertEquals(subject, found.get().newSubject());
    }

    @Test
    void findByIdempotencyKeyReturnsEmptyForMissingKey() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        assertTrue(ledgerRepository.findByIdempotencyKey(itemId, "nonexistent").isEmpty());
    }

    @Test
    void findByIdempotencyKeyReturnsEmptyForBlankKey() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        assertTrue(ledgerRepository.findByIdempotencyKey(itemId, "").isEmpty());
    }

    @Test
    void findCurrentOwnershipReturnsEmptyForUnknownItem() throws Exception {
        assertTrue(ledgerRepository.findCurrentOwnership(TrackedItemId.random()).isEmpty());
    }

    @Test
    void findHistoryReturnsEmptyForUnknownItem() throws Exception {
        assertTrue(ledgerRepository.findHistory(TrackedItemId.random(), 10).isEmpty());
    }

    @Test
    void countReturnsTotalAcrossAllItems() throws Exception {
        TrackedItemId id1 = createTrackedItem();
        TrackedItemId id2 = createTrackedItem();
        ledgerRepository.append(entry(id1, 1, null, OwnershipSubject.player(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED, "k1"));
        ledgerRepository.append(entry(id2, 1, null, OwnershipSubject.entity(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED, "k2"));
        assertEquals(2L, ledgerRepository.count());
    }

    @Test
    void previousSubjectIsPersistedAndReadBack() throws Exception {
        TrackedItemId itemId = createTrackedItem();
        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID(), "Steve");
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID(), "Zombie");

        ledgerRepository.append(entry(itemId, 1, null, s1, OwnershipTransitionReason.TRACKED, "k1"));
        ledgerRepository.append(entry(itemId, 2, s1, s2, OwnershipTransitionReason.TRANSFERRED, "k2"));

        List<OwnershipLedgerEntry> history = ledgerRepository.findHistory(itemId, 10);
        OwnershipLedgerEntry second = history.get(0);
        assertTrue(second.optionalPreviousSubject().isPresent());
        assertEquals(s1, second.optionalPreviousSubject().get());
    }

    private TrackedItemId createTrackedItem() throws Exception {
        TrackedItemId id = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        trackedItemRepository.create(new TrackedItemRecord(
                id, now, now, now,
                new ContentKey("minecraft", "diamond_sword"),
                "minecraft", "minecraft:diamond_sword", "", null, "", ""
        ));
        return id;
    }

    private static OwnershipLedgerEntry entry(
            TrackedItemId itemId, int seq,
            OwnershipSubject prev, OwnershipSubject next,
            OwnershipTransitionReason reason, String idempotencyKey
    ) {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        return new OwnershipLedgerEntry(
                UUID.randomUUID().toString(),
                itemId, seq, prev, next, reason,
                now, now, "test", "", idempotencyKey, ""
        );
    }
}
