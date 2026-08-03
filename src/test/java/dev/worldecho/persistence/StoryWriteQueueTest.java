package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.memory.MemoryEventType;
import dev.worldecho.domain.memory.StoryMemoryEvent;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryWriteQueueTest {

    @Test
    void drainsEverySubmittedEventOnShutdown() {
        RecordingRepository repository = new RecordingRepository();
        StoryWriteQueue queue = new StoryWriteQueue(repository, this::rethrow, 128, 8);
        queue.start();

        for (int index = 0; index < 50; index++) {
            assertTrue(queue.submit(event()));
        }

        assertTrue(queue.shutdown(Duration.ofSeconds(5)));
        assertEquals(50, repository.stored.size());
        assertEquals(50, queue.status().written());
        assertEquals(0, queue.status().dropped());
    }

    @Test
    void dropsInsteadOfBlockingWhenTheQueueIsFull() {
        RecordingRepository repository = new RecordingRepository();
        StoryWriteQueue queue = new StoryWriteQueue(repository, this::rethrow, 16, 1);

        // The writer thread is never started, so nothing is consumed.
        int accepted = 0;
        for (int index = 0; index < 40; index++) {
            if (queue.submit(event())) {
                accepted++;
            }
        }

        assertEquals(16, accepted);
        assertEquals(24, queue.status().dropped());
        assertEquals(16, queue.status().pending());
    }

    @Test
    void writeFailuresAreReportedAndCounted() {
        FailingRepository repository = new FailingRepository();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        StoryWriteQueue queue = new StoryWriteQueue(repository, errors::add, 32, 1);
        queue.start();

        queue.submit(event());
        queue.shutdown(Duration.ofSeconds(5));

        assertEquals(1, errors.size());
        assertEquals(1, queue.status().failed());
        assertEquals(0, queue.status().written());
    }

    @Test
    void submissionsAfterShutdownAreRejected() {
        RecordingRepository repository = new RecordingRepository();
        StoryWriteQueue queue = new StoryWriteQueue(repository, this::rethrow, 32, 4);
        queue.start();
        queue.shutdown(Duration.ofSeconds(5));

        assertFalse(queue.submit(event()));
        assertTrue(repository.stored.isEmpty());
    }

    private void rethrow(Throwable throwable) {
        throw new AssertionError("unexpected write failure", throwable);
    }

    private static StoryMemoryEvent event() {
        return new StoryMemoryEvent(
                UUID.randomUUID(),
                MemoryEventType.PLAYER_KILLED_BY_ENTITY,
                Instant.now(),
                UUID.randomUUID(),
                0, 64, 0,
                UUID.randomUUID(),
                new ContentKey("vanilla", "minecraft:zombie"),
                UUID.randomUUID().toString(),
                null,
                "",
                ""
        );
    }

    private static class RecordingRepository implements StoryEventRepository {

        private final List<StoryMemoryEvent> stored = new CopyOnWriteArrayList<>();

        @Override
        public void insert(StoryMemoryEvent event) {
            stored.add(event);
        }

        @Override
        public int insertAll(Collection<StoryMemoryEvent> events) throws SQLException {
            stored.addAll(events);
            return events.size();
        }

        @Override
        public List<StoryMemoryEvent> findRecent(int count) {
            return List.copyOf(stored);
        }

        @Override
        public long count() {
            return stored.size();
        }
    }

    private static final class FailingRepository extends RecordingRepository {

        @Override
        public int insertAll(Collection<StoryMemoryEvent> events) throws SQLException {
            throw new SQLException("disk is on fire");
        }
    }
}
