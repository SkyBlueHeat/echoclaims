package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that one death evidence unit (snapshot + items + incident) is atomic:
 * either all three persist or none persist.
 */
class EvidenceTransactionAtomicityTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private EvidenceStore evidenceStore;
    private InventorySnapshotRepository snapshotRepo;
    private IncidentRepository incidentRepo;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir.resolve("atomicity_test.db"));
        database.initialize();
        evidenceStore = new SqliteEvidenceStore(database);
        snapshotRepo = new SqliteInventorySnapshotRepository(database);
        incidentRepo = new SqliteIncidentRepository(database);
    }

    private InventorySnapshot createSnapshot(UUID id, UUID playerUuid) {
        return new InventorySnapshot(
                id, playerUuid, CaptureReason.PRE_DEATH, 10_000L,
                "world", new Coordinates(10, 20, 30), "survival",
                18.5, 18, 5, 50,
                List.of(new SnapshotItem("0", "minecraft:diamond_sword", 1,
                        "serialized", "oraxen:blade", 42, "Excalibur",
                        10, 1561, Map.of("minecraft:sharpness", 5))),
                List.of(new SnapshotItem("36", "minecraft:diamond_helmet", 1,
                        "", "", 20, "", 0, 363, Map.of())),
                new SnapshotItem("40", "minecraft:shield", 1, "", "", 8, "", 0, 336, Map.of()),
                null,
                1
        );
    }

    private Incident createIncident(UUID id, UUID playerUuid, UUID snapshotId, String dedupKey) {
        return new Incident(
                id, IncidentType.PLAYER_DEATH, playerUuid,
                10_000L, "world", new Coordinates(10, 20, 30),
                "ENTITY_ATTACK", UUID.randomUUID(), "minecraft:zombie",
                snapshotId, null, IncidentStatus.OPEN,
                Map.of("keepInventory", "false"), dedupKey
        );
    }

    @Test
    void successfulInsertPersistsAllThree() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        evidenceStore.insertDeathEvidence(
                createSnapshot(snapshotId, player),
                createIncident(incidentId, player, snapshotId, "dedup-ok")
        );

        assertTrue(snapshotRepo.findById(snapshotId).isPresent());
        assertTrue(incidentRepo.findById(incidentId).isPresent());
        assertEquals(1, snapshotRepo.count());
        assertEquals(1, incidentRepo.count());
    }

    @Test
    void duplicateDeduplicationKeyRollsBackSnapshot() throws Exception {
        UUID snapshotId1 = UUID.randomUUID();
        UUID incidentId1 = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        evidenceStore.insertDeathEvidence(
                createSnapshot(snapshotId1, player),
                createIncident(incidentId1, player, snapshotId1, "same-key")
        );

        UUID snapshotId2 = UUID.randomUUID();
        UUID incidentId2 = UUID.randomUUID();

        assertThrows(SQLException.class, () ->
                evidenceStore.insertDeathEvidence(
                        createSnapshot(snapshotId2, player),
                        createIncident(incidentId2, player, snapshotId2, "same-key")
                )
        );

        assertFalse(snapshotRepo.findById(snapshotId2).isPresent(),
                "Second snapshot must not persist after rollback");
        assertFalse(incidentRepo.findById(incidentId2).isPresent(),
                "Second incident must not persist after rollback");
        assertEquals(1, snapshotRepo.count(),
                "Only the first snapshot should exist");
        assertEquals(1, incidentRepo.count(),
                "Only the first incident should exist");
    }

    @Test
    void incidentInsertFailureLeavesNoOrphanSnapshot() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        InventorySnapshot snapshot = createSnapshot(snapshotId, player);

        Incident incidentWithBadSnapshotRef = new Incident(
                UUID.randomUUID(), IncidentType.PLAYER_DEATH, player,
                10_000L, "world", new Coordinates(10, 20, 30),
                "ENTITY_ATTACK", null, "",
                UUID.randomUUID(),
                null, IncidentStatus.OPEN,
                Map.of(), "dedup-bad-fk"
        );

        assertThrows(SQLException.class, () ->
                evidenceStore.insertDeathEvidence(snapshot, incidentWithBadSnapshotRef)
        );

        assertFalse(snapshotRepo.findById(snapshotId).isPresent(),
                "Snapshot must not persist after incident FK violation rollback");
        assertEquals(0, snapshotRepo.count());
    }

    @Test
    void successfulRetryAfterRollback() throws Exception {
        UUID player = UUID.randomUUID();
        UUID snapshotId1 = UUID.randomUUID();

        Incident badIncident = new Incident(
                UUID.randomUUID(), IncidentType.PLAYER_DEATH, player,
                10_000L, "world", new Coordinates(10, 20, 30),
                "ENTITY_ATTACK", null, "",
                UUID.randomUUID(),
                null, IncidentStatus.OPEN,
                Map.of(), "dedup-retry"
        );

        assertThrows(SQLException.class, () ->
                evidenceStore.insertDeathEvidence(createSnapshot(snapshotId1, player), badIncident)
        );

        assertFalse(snapshotRepo.findById(snapshotId1).isPresent());

        UUID snapshotId2 = UUID.randomUUID();
        UUID incidentId2 = UUID.randomUUID();

        evidenceStore.insertDeathEvidence(
                createSnapshot(snapshotId2, player),
                createIncident(incidentId2, player, snapshotId2, "dedup-retry")
        );

        assertTrue(snapshotRepo.findById(snapshotId2).isPresent());
        assertTrue(incidentRepo.findById(incidentId2).isPresent());
        assertEquals(1, snapshotRepo.count());
        assertEquals(1, incidentRepo.count());
    }

    @Test
    void postRespawnSnapshotInsertIsAtomicWithIncidentUpdate() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID postSnapshotId = UUID.randomUUID();

        evidenceStore.insertDeathEvidence(
                createSnapshot(snapshotId, player),
                createIncident(incidentId, player, snapshotId, "dedup-post-atomic")
        );

        InventorySnapshot postSnapshot = new InventorySnapshot(
                postSnapshotId, player, CaptureReason.POST_RESPAWN, 12_000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        );

        evidenceStore.insertPostRespawnSnapshot(incidentId, postSnapshot);

        Optional<InventorySnapshot> found = snapshotRepo.findById(postSnapshotId);
        assertTrue(found.isPresent());

        Optional<Incident> incident = incidentRepo.findById(incidentId);
        assertTrue(incident.isPresent());
        assertTrue(incident.get().hasPostEventSnapshot());
        assertEquals(postSnapshotId, incident.get().postEventSnapshotUuid());
    }

    @Test
    void postRespawnSnapshotWithNonexistentIncidentRollsBack() throws Exception {
        UUID player = UUID.randomUUID();
        UUID postSnapshotId = UUID.randomUUID();

        InventorySnapshot postSnapshot = new InventorySnapshot(
                postSnapshotId, player, CaptureReason.POST_RESPAWN, 12_000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        );

        assertThrows(SQLException.class, () ->
                evidenceStore.insertPostRespawnSnapshot(UUID.randomUUID(), postSnapshot)
        );

        assertFalse(snapshotRepo.findById(postSnapshotId).isPresent(),
                "Post-respawn snapshot must not persist after incident update failure rollback");
    }

    @Test
    void snapshotWithItemsPersistsAllItemsAtomically() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        InventorySnapshot snapshot = new InventorySnapshot(
                snapshotId, player, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0,
                List.of(
                        new SnapshotItem("0", "minecraft:stone", 32, "data", "", 5, "", 0, 0, Map.of()),
                        new SnapshotItem("1", "minecraft:dirt", 64, "data2", "", 2, "", 0, 0, Map.of()),
                        new SnapshotItem("2", "minecraft:cobblestone", 16, "data3", "", 1, "", 0, 0, Map.of())
                ),
                List.of(
                        new SnapshotItem("36", "minecraft:diamond_boots", 1, "data4", "", 15, "", 0, 260, Map.of())
                ),
                new SnapshotItem("40", "minecraft:shield", 1, "data5", "", 8, "", 0, 336, Map.of()),
                null,
                1
        );

        Incident incident = createIncident(incidentId, player, snapshotId, "dedup-items");

        evidenceStore.insertDeathEvidence(snapshot, incident);

        Optional<InventorySnapshot> found = snapshotRepo.findById(snapshotId);
        assertTrue(found.isPresent());
        assertEquals(3, found.get().inventoryItems().size());
        assertEquals(1, found.get().armorItems().size());
        assertTrue(found.get().hasOffhandItem());
    }
}
