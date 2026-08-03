package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TrackedItemRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTrackedItemRepositoryTest {

    @TempDir
    Path tempDir;

    private TrackedItemRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("tracked-test.db"));
        database.initialize();
        repository = new SqliteTrackedItemRepository(database);
    }

    @Test
    void createAndFindById() throws Exception {
        TrackedItemId id = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        TrackedItemRecord record = new TrackedItemRecord(
                id, now, now, now,
                new ContentKey("minecraft", "diamond_sword"),
                "minecraft",
                "minecraft:diamond_sword",
                "Excalibur",
                45,
                "admin-track",
                "player:abc-123"
        );

        assertEquals(TrackedItemRepository.CreateResult.CREATED, repository.create(record));
        Optional<TrackedItemRecord> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals(record, found.get());
    }

    @Test
    void createIsIdempotent() throws Exception {
        TrackedItemId id = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        TrackedItemRecord record = new TrackedItemRecord(
                id, now, now, now,
                new ContentKey("minecraft", "netherite_pickaxe"),
                "minecraft",
                "minecraft:netherite_pickaxe",
                "", null, "", ""
        );

        assertEquals(TrackedItemRepository.CreateResult.CREATED, repository.create(record));
        assertEquals(TrackedItemRepository.CreateResult.ALREADY_EXISTS, repository.create(record));
    }

    @Test
    void existsReturnsTrueAfterCreate() throws Exception {
        TrackedItemId id = TrackedItemId.random();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        TrackedItemRecord record = new TrackedItemRecord(
                id, now, now, now,
                new ContentKey("minecraft", "elytra"),
                "minecraft", "minecraft:elytra", "", null, "", ""
        );

        assertFalse(repository.exists(id));
        repository.create(record);
        assertTrue(repository.exists(id));
    }

    @Test
    void observeUpdatesLastSeenAt() throws Exception {
        TrackedItemId id = TrackedItemId.random();
        Instant created = Instant.parse("2026-07-30T12:00:00Z");
        TrackedItemRecord record = new TrackedItemRecord(
                id, created, created, created,
                new ContentKey("minecraft", "trident"),
                "minecraft", "minecraft:trident", "", null, "", ""
        );

        repository.create(record);
        long newLastSeen = Instant.parse("2026-07-31T12:00:00Z").toEpochMilli();
        repository.observe(id, newLastSeen);

        Optional<TrackedItemRecord> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals(newLastSeen, found.get().lastSeenAt().toEpochMilli());
        assertEquals(created.toEpochMilli(), found.get().createdAt().toEpochMilli());
    }

    @Test
    void countReflectsCreatedRecords() throws Exception {
        assertEquals(0L, repository.count());
        createSimpleRecord(TrackedItemId.random());
        createSimpleRecord(TrackedItemId.random());
        assertEquals(2L, repository.count());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() throws Exception {
        assertTrue(repository.findById(TrackedItemId.random()).isEmpty());
    }

    private void createSimpleRecord(TrackedItemId id) throws Exception {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        TrackedItemRecord record = new TrackedItemRecord(
                id, now, now, now,
                new ContentKey("minecraft", "stick"),
                "minecraft", "minecraft:stick", "", null, "", ""
        );
        repository.create(record);
    }
}
