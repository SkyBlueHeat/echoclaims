package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.item.OwnershipResult;
import dev.worldecho.domain.item.OwnershipResultStatus;
import dev.worldecho.domain.item.OwnershipState;
import dev.worldecho.domain.item.OwnershipSubject;
import dev.worldecho.domain.item.OwnershipTransitionReason;
import dev.worldecho.domain.item.OwnershipTransitionService;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TrackedItemRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests covering the full flow: track item → create record →
 * create ownership sequence → restart → ownership still present → append
 * transition → history count increases.
 */
class ItemOwnershipIntegrationTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private TrackedItemRepository trackedItemRepository;
    private OwnershipLedgerRepository ledgerRepository;
    private OwnershipTransitionService transitionService;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir.resolve("integration-test.db"));
        database.initialize();
        trackedItemRepository = new SqliteTrackedItemRepository(database);
        ledgerRepository = new SqliteOwnershipLedgerRepository(database);
        transitionService = new OwnershipTransitionService(
                trackedItemRepository, ledgerRepository,
                Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneId.of("UTC"))
        );
    }

    @Test
    void trackItemCreatesRecordAndOwnershipSequence1() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "tracked", "player:" + UUID.randomUUID()
        );

        assertEquals(TrackedItemRepository.CreateResult.CREATED,
                trackedItemRepository.create(record));
        assertTrue(trackedItemRepository.exists(itemId));

        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());
        OwnershipResult result = transitionService.transition(
                itemId, subject, OwnershipTransitionReason.TRACKED,
                "track-" + itemId, "admin", "");

        assertEquals(OwnershipResultStatus.RECORDED, result.status());
        assertEquals(1, result.entry().sequenceNumber());

        Optional<OwnershipState> state = ledgerRepository.findCurrentOwnership(itemId);
        assertTrue(state.isPresent());
        assertEquals(subject, state.get().currentSubject());
        assertEquals(1, state.get().latestSequence());
    }

    @Test
    void restartRepositoryCurrentOwnerStillPresent() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");
        UUID playerUuid = UUID.randomUUID();

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "tracked", "player:" + playerUuid
        );

        trackedItemRepository.create(record);
        transitionService.transition(
                itemId, OwnershipSubject.player(playerUuid),
                OwnershipTransitionReason.TRACKED,
                "track-" + itemId, "admin", "");

        DatabaseManager restarted = new DatabaseManager(database.databasePath());
        restarted.initialize();
        OwnershipLedgerRepository restartedLedger = new SqliteOwnershipLedgerRepository(restarted);

        Optional<OwnershipState> state = restartedLedger.findCurrentOwnership(itemId);
        assertTrue(state.isPresent());
        assertEquals(playerUuid.toString(), state.get().currentSubject().stableId());
        assertEquals(1, state.get().latestSequence());
    }

    @Test
    void appendTransitionHistoryCountIncreases() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "tracked", "player:" + UUID.randomUUID()
        );

        trackedItemRepository.create(record);

        OwnershipSubject s1 = OwnershipSubject.player(UUID.randomUUID());
        OwnershipSubject s2 = OwnershipSubject.entity(UUID.randomUUID());

        transitionService.transition(
                itemId, s1, OwnershipTransitionReason.TRACKED, "k1", "", "");
        assertEquals(1, ledgerRepository.countHistory(itemId));

        transitionService.transition(
                itemId, s2, OwnershipTransitionReason.TRANSFERRED, "k2", "", "");
        assertEquals(2, ledgerRepository.countHistory(itemId));
    }

    @Test
    void historyOrderingIsDescendingBySequence() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "tracked", "player:" + UUID.randomUUID()
        );

        trackedItemRepository.create(record);

        transitionService.transition(
                itemId, OwnershipSubject.player(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED, "k1", "", "");
        transitionService.transition(
                itemId, OwnershipSubject.entity(UUID.randomUUID()),
                OwnershipTransitionReason.TRANSFERRED, "k2", "", "");
        transitionService.transition(
                itemId, OwnershipSubject.system("admin"),
                OwnershipTransitionReason.ADMIN_ASSIGNMENT, "k3", "", "");

        List<dev.worldecho.domain.item.OwnershipLedgerEntry> history =
                ledgerRepository.findHistory(itemId, 10);

        assertEquals(3, history.size());
        assertEquals(3, history.get(0).sequenceNumber());
        assertEquals(2, history.get(1).sequenceNumber());
        assertEquals(1, history.get(2).sequenceNumber());
    }

    @Test
    void existingStoryEventsRemainReadable() throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("events-test.db"));
        db.initialize();
        SqliteStoryEventRepository storyRepo = new SqliteStoryEventRepository(db);

        dev.worldecho.domain.memory.StoryMemoryEvent event =
                new dev.worldecho.domain.memory.StoryMemoryEvent(
                        UUID.randomUUID(),
                        dev.worldecho.domain.memory.MemoryEventType.PLAYER_KILLED_BY_ENTITY,
                        Instant.parse("2026-07-30T12:00:00Z"),
                        UUID.randomUUID(), 0, 64, 0,
                        UUID.randomUUID(),
                        new ContentKey("minecraft", "zombie"),
                        UUID.randomUUID().toString(),
                        new ContentKey("minecraft", "diamond_sword"),
                        "content=minecraft:diamond_sword",
                        "world=overworld"
                );

        storyRepo.insert(event);

        DatabaseManager restarted = new DatabaseManager(db.databasePath());
        restarted.initialize();
        SqliteStoryEventRepository restartedRepo = new SqliteStoryEventRepository(restarted);

        List<dev.worldecho.domain.memory.StoryMemoryEvent> recent =
                restartedRepo.findRecent(10);

        assertEquals(1, recent.size());
        assertEquals(event.id(), recent.get(0).id());
    }

    @Test
    void differentItemsCanBothUseSequence1() throws Exception {
        TrackedItemId id1 = TrackedItemId.random();
        TrackedItemId id2 = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        trackedItemRepository.create(new TrackedItemRecord(
                id1, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "tracked", "player:" + UUID.randomUUID()));
        trackedItemRepository.create(new TrackedItemRecord(
                id2, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "tracked", "player:" + UUID.randomUUID()));

        transitionService.transition(
                id1, OwnershipSubject.player(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED, "k1a", "", "");
        transitionService.transition(
                id2, OwnershipSubject.player(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED, "k2a", "", "");

        Optional<OwnershipState> s1 = ledgerRepository.findCurrentOwnership(id1);
        Optional<OwnershipState> s2 = ledgerRepository.findCurrentOwnership(id2);

        assertTrue(s1.isPresent());
        assertTrue(s2.isPresent());
        assertEquals(1, s1.get().latestSequence());
        assertEquals(1, s2.get().latestSequence());
    }
}
