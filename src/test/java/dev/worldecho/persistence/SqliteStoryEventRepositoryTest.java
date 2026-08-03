package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.memory.MemoryEventType;
import dev.worldecho.domain.memory.StoryMemoryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteStoryEventRepositoryTest {

    @TempDir
    Path tempDir;

    private StoryEventRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("worldecho-test.db"));
        database.initialize();
        repository = new SqliteStoryEventRepository(database);
    }

    @Test
    void insertsAndReadsBackEveryColumn() throws Exception {
        StoryMemoryEvent event = event(Instant.parse("2026-07-29T11:00:00Z"));

        repository.insert(event);

        assertEquals(1L, repository.count());
        StoryMemoryEvent stored = repository.findRecent(10).getFirst();
        assertEquals(event, stored);
    }

    @Test
    void storesEventsWithoutAnItem() throws Exception {
        StoryMemoryEvent event = new StoryMemoryEvent(
                UUID.randomUUID(),
                MemoryEventType.PLAYER_KILLED_BY_ENTITY,
                Instant.parse("2026-07-29T11:00:00Z"),
                UUID.randomUUID(),
                0, 64, 0,
                UUID.randomUUID(),
                new ContentKey("vanilla", "minecraft:creeper"),
                UUID.randomUUID().toString(),
                null,
                "",
                "inspectedDrops=0"
        );

        repository.insert(event);

        StoryMemoryEvent stored = repository.findRecent(1).getFirst();
        assertTrue(stored.optionalItem().isEmpty());
        assertEquals(event, stored);
    }

    @Test
    void returnsMostRecentEventsFirstAndRespectsTheLimit() throws Exception {
        repository.insertAll(List.of(
                event(Instant.parse("2026-07-29T10:00:00Z")),
                event(Instant.parse("2026-07-29T12:00:00Z")),
                event(Instant.parse("2026-07-29T11:00:00Z"))
        ));

        List<StoryMemoryEvent> recent = repository.findRecent(2);

        assertEquals(3L, repository.count());
        assertEquals(2, recent.size());
        assertEquals(Instant.parse("2026-07-29T12:00:00Z"), recent.get(0).occurredAt());
        assertEquals(Instant.parse("2026-07-29T11:00:00Z"), recent.get(1).occurredAt());
    }

    @Test
    void neverOverwritesExistingHistory() throws Exception {
        StoryMemoryEvent event = event(Instant.parse("2026-07-29T11:00:00Z"));

        repository.insert(event);
        repository.insert(event);

        assertEquals(1L, repository.count());
    }

    private static StoryMemoryEvent event(Instant occurredAt) {
        return new StoryMemoryEvent(
                UUID.randomUUID(),
                MemoryEventType.PLAYER_KILLED_BY_ENTITY,
                occurredAt,
                UUID.randomUUID(),
                12, 64, -7,
                UUID.randomUUID(),
                new ContentKey("vanilla", "minecraft:zombie"),
                UUID.randomUUID().toString(),
                new ContentKey("vanilla", "minecraft:diamond_sword"),
                "material=minecraft:diamond_sword;score=45",
                "test=true"
        );
    }
}
