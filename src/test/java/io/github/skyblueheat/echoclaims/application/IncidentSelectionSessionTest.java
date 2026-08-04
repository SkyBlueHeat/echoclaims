package io.github.skyblueheat.echoclaims.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IncidentSelectionSessionTest {

    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final UUID INCIDENT_1 = UUID.randomUUID();
    private static final UUID INCIDENT_2 = UUID.randomUUID();
    private static final UUID INCIDENT_3 = UUID.randomUUID();

    @Test
    void storeAndResolveReturnsCorrectIncident() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1, INCIDENT_2, INCIDENT_3));

        assertEquals(Optional.of(INCIDENT_1), session.resolve(PLAYER_A, 1));
        assertEquals(Optional.of(INCIDENT_2), session.resolve(PLAYER_A, 2));
        assertEquals(Optional.of(INCIDENT_3), session.resolve(PLAYER_A, 3));
    }

    @Test
    void resolveWithoutSessionReturnsEmpty() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        assertTrue(session.resolve(PLAYER_A, 1).isEmpty());
    }

    @Test
    void expiredSessionReturnsEmpty() throws Exception {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofMillis(50), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1));
        Thread.sleep(100);
        assertTrue(session.resolve(PLAYER_A, 1).isEmpty());
    }

    @Test
    void clearRemovesSession() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1));
        session.clear(PLAYER_A);
        assertTrue(session.resolve(PLAYER_A, 1).isEmpty());
    }

    @Test
    void clearAllRemovesAllSessions() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1));
        session.store(PLAYER_B, List.of(INCIDENT_2));
        session.clearAll();
        assertTrue(session.resolve(PLAYER_A, 1).isEmpty());
        assertTrue(session.resolve(PLAYER_B, 1).isEmpty());
    }

    @Test
    void indexOutOfRangeReturnsEmpty() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1, INCIDENT_2));
        assertTrue(session.resolve(PLAYER_A, 0).isEmpty());
        assertTrue(session.resolve(PLAYER_A, 3).isEmpty());
    }

    @Test
    void newIndexDoesNotRemapExistingIndex() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1, INCIDENT_2));
        assertEquals(Optional.of(INCIDENT_1), session.resolve(PLAYER_A, 1));
        assertEquals(Optional.of(INCIDENT_2), session.resolve(PLAYER_A, 2));

        session.store(PLAYER_A, List.of(INCIDENT_3, INCIDENT_1, INCIDENT_2));
        assertEquals(Optional.of(INCIDENT_3), session.resolve(PLAYER_A, 1));
        assertEquals(Optional.of(INCIDENT_1), session.resolve(PLAYER_A, 2));
        assertNotEquals(Optional.of(INCIDENT_1), session.resolve(PLAYER_A, 1));
    }

    @Test
    void boundedPlayersEvictsOldest() throws Exception {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 2);
        session.store(PLAYER_A, List.of(INCIDENT_1));
        Thread.sleep(10);
        session.store(PLAYER_B, List.of(INCIDENT_2));
        Thread.sleep(10);
        session.store(UUID.randomUUID(), List.of(INCIDENT_3));

        assertTrue(session.resolve(PLAYER_A, 1).isEmpty(),
                "Oldest session should have been evicted");
        assertTrue(session.resolve(PLAYER_B, 1).isPresent(),
                "Second session should still be present");
    }

    @Test
    void restartClearsAllSessions() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1));
        session.clearAll();
        assertTrue(session.resolve(PLAYER_A, 1).isEmpty());
    }

    @Test
    void concurrentAccessIsSafe() throws Exception {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 100);
        int threadCount = 16;
        int iterations = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            final UUID player = UUID.randomUUID();
            final int tid = t;
            pool.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    for (int i = 0; i < iterations; i++) {
                        session.store(player, List.of(UUID.randomUUID(), UUID.randomUUID()));
                        session.resolve(player, 1);
                        session.resolve(player, 2);
                        if (i % 10 == 0) {
                            session.clear(player);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(0, errors.get(), "Concurrent access should not produce errors");
    }

    @Test
    void trackedPlayerCountReflectsActiveSessions() {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofSeconds(60), 10);
        assertEquals(0, session.trackedPlayerCount());
        session.store(PLAYER_A, List.of(INCIDENT_1));
        session.store(PLAYER_B, List.of(INCIDENT_2));
        assertEquals(2, session.trackedPlayerCount());
        session.clear(PLAYER_A);
        assertEquals(1, session.trackedPlayerCount());
    }

    @Test
    void expiredEntriesCleanedUpOnStore() throws Exception {
        IncidentSelectionSession session = new IncidentSelectionSession(Duration.ofMillis(50), 10);
        session.store(PLAYER_A, List.of(INCIDENT_1));
        Thread.sleep(100);
        session.store(PLAYER_B, List.of(INCIDENT_2));
        assertEquals(1, session.trackedPlayerCount(), "Expired entry should be cleaned up");
    }
}
