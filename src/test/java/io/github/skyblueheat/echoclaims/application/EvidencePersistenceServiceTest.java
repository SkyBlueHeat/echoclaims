package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.IncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteIncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteInventorySnapshotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidencePersistenceServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private InventorySnapshotRepository snapshotRepo;
    private IncidentRepository incidentRepo;
    private EvidenceMetrics metrics;
    private EvidencePersistenceService service;
    private final AtomicInteger errorCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir.resolve("evidence_svc.db"));
        database.initialize();
        snapshotRepo = new SqliteInventorySnapshotRepository(database);
        incidentRepo = new SqliteIncidentRepository(database);
        metrics = new EvidenceMetrics();
        errorCount.set(0);
        service = new EvidencePersistenceService(
                snapshotRepo, incidentRepo, metrics,
                throwable -> errorCount.incrementAndGet(),
                32,
                Logger.getLogger("test")
        );
    }

    @AfterEach
    void tearDown() {
        service.shutdown(Duration.ofSeconds(5));
        // SQLiteDataSource is garbage-collected; no explicit close needed.
    }

    private InventorySnapshot createSnapshot(UUID id, UUID playerUuid) {
        return new InventorySnapshot(
                id, playerUuid, CaptureReason.PRE_DEATH, 10_000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        );
    }

    private Incident createIncident(UUID id, UUID playerUuid, UUID snapshotId, String dedupKey) {
        return new Incident(
                id, IncidentType.PLAYER_DEATH, playerUuid,
                10_000L, "world", new Coordinates(0, 0, 0),
                "", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), dedupKey
        );
    }

    @Test
    void submitDeathCapturePersistsSnapshotAndIncident() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        assertTrue(service.submitDeathCapture(
                createSnapshot(snapshotId, player),
                createIncident(incidentId, player, snapshotId, "dedup-1")
        ));

        awaitMetrics(() -> metrics.persistedIncidents() >= 1, 5_000);

        assertEquals(1, metrics.acceptedCaptures());
        assertEquals(1, metrics.persistedSnapshots());
        assertEquals(1, metrics.persistedIncidents());
        assertEquals(0, metrics.failedPersistence());
        assertEquals(0, errorCount.get());
    }

    @Test
    void duplicateIncidentIsCountedAsDuplicate() throws Exception {
        UUID snapshotId1 = UUID.randomUUID();
        UUID snapshotId2 = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        service.submitDeathCapture(
                createSnapshot(snapshotId1, player),
                createIncident(UUID.randomUUID(), player, snapshotId1, "same-dedup")
        );
        awaitMetrics(() -> metrics.persistedIncidents() >= 1, 5_000);

        service.submitDeathCapture(
                createSnapshot(snapshotId2, player),
                createIncident(UUID.randomUUID(), player, snapshotId2, "same-dedup")
        );
        awaitMetrics(() -> metrics.duplicateIncidents() >= 1, 5_000);

        assertEquals(2, metrics.acceptedCaptures());
        assertEquals(2, metrics.persistedSnapshots());
        assertEquals(1, metrics.persistedIncidents());
        assertEquals(1, metrics.duplicateIncidents());
        assertEquals(0, metrics.failedPersistence());
        assertEquals(0, errorCount.get());
    }

    @Test
    void shutdownRejectsNewSubmissions() throws Exception {
        service.shutdown(Duration.ofSeconds(1));

        UUID player = UUID.randomUUID();
        boolean accepted = service.submitDeathCapture(
                createSnapshot(UUID.randomUUID(), player),
                createIncident(UUID.randomUUID(), player, UUID.randomUUID(), "after-shutdown")
        );

        assertFalse(accepted);
        assertEquals(1, metrics.rejectedCaptures());
    }

    @Test
    void submitPostRespawnSnapshotPersistsAndLinks() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID postSnapshotId = UUID.randomUUID();

        service.submitDeathCapture(
                createSnapshot(snapshotId, player),
                createIncident(incidentId, player, snapshotId, "dedup-post")
        );
        awaitMetrics(() -> metrics.persistedIncidents() >= 1, 5_000);

        service.submitPostRespawnSnapshot(incidentId,
                new InventorySnapshot(
                        postSnapshotId, player, CaptureReason.POST_RESPAWN, 12_000L,
                        "world", new Coordinates(0, 0, 0), "survival",
                        20, 20, 0, 0, List.of(), List.of(), null, null, 1
                ));
        awaitMetrics(() -> metrics.persistedSnapshots() >= 2, 5_000);

        assertEquals(2, metrics.persistedSnapshots());

        var incident = incidentRepo.findById(incidentId);
        assertTrue(incident.isPresent());
        assertTrue(incident.get().hasPostEventSnapshot());
        assertEquals(postSnapshotId, incident.get().postEventSnapshotUuid());
    }

    private void awaitMetrics(java.util.function.Supplier<Boolean> condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
