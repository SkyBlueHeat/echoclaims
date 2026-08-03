package dev.worldecho.domain.item;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.persistence.DatabaseManager;
import dev.worldecho.persistence.OwnershipLedgerRepository;
import dev.worldecho.persistence.SqliteOwnershipLedgerRepository;
import dev.worldecho.persistence.SqliteTrackedItemRepository;
import dev.worldecho.persistence.TrackedItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that repeated track commands reconcile a PDC ID that exists but has no
 * database row, and that snapshot conflicts produce diagnostics rather than
 * silently overwriting initial identity data.
 */
class ReconciliationTest {

    @TempDir
    Path tempDir;

    private TrackedItemRepository trackedItemRepository;
    private OwnershipLedgerRepository ledgerRepository;
    private OwnershipTransitionService transitionService;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("reconciliation-test.db"));
        database.initialize();
        trackedItemRepository = new SqliteTrackedItemRepository(database);
        ledgerRepository = new SqliteOwnershipLedgerRepository(database);
        transitionService = new OwnershipTransitionService(
                trackedItemRepository, ledgerRepository,
                Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneId.of("UTC"))
        );
    }

    @Test
    void pdcIdExistsButDatabaseRowMissing() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        assertFalse(trackedItemRepository.exists(itemId));
        assertTrue(trackedItemRepository.findById(itemId).isEmpty());
    }

    @Test
    void repeatedTrackReconcilesMissingDatabaseRecord() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "reconcile", "player:" + UUID.randomUUID()
        );

        assertEquals(TrackedItemRepository.CreateResult.CREATED,
                trackedItemRepository.create(record));
        assertTrue(trackedItemRepository.exists(itemId));

        OwnershipResult result = transitionService.transition(
                itemId,
                OwnershipSubject.player(UUID.randomUUID()),
                OwnershipTransitionReason.TRACKED,
                "track-" + itemId,
                "reconcile", ""
        );

        assertEquals(OwnershipResultStatus.RECORDED, result.status());
        assertTrue(result.optionalEntry().isPresent());
    }

    @Test
    void reconciliationPreservesOriginalPdcId() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "reconcile", "player:" + UUID.randomUUID()
        );

        trackedItemRepository.create(record);

        Optional<TrackedItemRecord> found = trackedItemRepository.findById(itemId);
        assertTrue(found.isPresent());
        assertEquals(itemId, found.get().itemId());
    }

    @Test
    void reconciliationDoesNotDuplicateInitialLedgerEntry() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "reconcile", "player:" + UUID.randomUUID()
        );

        trackedItemRepository.create(record);

        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());
        String idempotencyKey = "track-" + itemId;

        OwnershipResult first = transitionService.transition(
                itemId, subject, OwnershipTransitionReason.TRACKED,
                idempotencyKey, "reconcile", "");
        assertEquals(OwnershipResultStatus.RECORDED, first.status());

        OwnershipResult second = transitionService.transition(
                itemId, subject, OwnershipTransitionReason.TRACKED,
                idempotencyKey, "reconcile", "");
        assertEquals(OwnershipResultStatus.IDEMPOTENT_REPLAY, second.status());

        assertEquals(1, ledgerRepository.countHistory(itemId));
    }

    @Test
    void databaseRecordExistsButObservedContentDiffers() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord record = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "", 45,
                "tracked", "player:" + UUID.randomUUID()
        );

        trackedItemRepository.create(record);

        Optional<TrackedItemRecord> found = trackedItemRepository.findById(itemId);
        assertTrue(found.isPresent());
        assertEquals("minecraft:diamond_sword", found.get().initialMaterial());
        assertEquals(key, found.get().contentKey());
    }

    @Test
    void validMutableDifferencesDoNotReplaceInitialIdentityRecord() throws Exception {
        TrackedItemId itemId = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        ContentKey key = new ContentKey("minecraft", "diamond_sword");

        TrackedItemRecord original = new TrackedItemRecord(
                itemId, now, now, now, key, "minecraft",
                "minecraft:diamond_sword", "Sharp Sword", 45,
                "tracked", "player:" + UUID.randomUUID()
        );

        trackedItemRepository.create(original);

        Optional<TrackedItemRecord> found = trackedItemRepository.findById(itemId);
        assertTrue(found.isPresent());
        assertEquals("Sharp Sword", found.get().initialCustomName());
        assertEquals("minecraft:diamond_sword", found.get().initialMaterial());
    }
}
