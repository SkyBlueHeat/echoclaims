package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private InventorySnapshotRepository snapshotRepo;
    private IncidentRepository incidentRepo;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir.resolve("evidence_test.db"));
        database.initialize();
        snapshotRepo = new SqliteInventorySnapshotRepository(database);
        incidentRepo = new SqliteIncidentRepository(database);
    }

    @AfterEach
    void tearDown() {
        // SQLiteDataSource is garbage-collected; no explicit close needed.
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
    void insertAndFindSnapshotById() throws Exception {
        UUID id = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        InventorySnapshot snapshot = createSnapshot(id, player);

        snapshotRepo.insert(snapshot);

        Optional<InventorySnapshot> found = snapshotRepo.findById(id);
        assertTrue(found.isPresent());
        InventorySnapshot retrieved = found.get();
        assertEquals(id, retrieved.id());
        assertEquals(player, retrieved.playerUuid());
        assertEquals(CaptureReason.PRE_DEATH, retrieved.captureReason());
        assertEquals(1, retrieved.inventoryItems().size());
        assertEquals(1, retrieved.armorItems().size());
        assertTrue(retrieved.hasOffhandItem());
        assertFalse(retrieved.hasCursorItem());

        SnapshotItem invItem = retrieved.inventoryItems().get(0);
        assertEquals("minecraft:diamond_sword", invItem.materialKey());
        assertEquals(42, invItem.score());
        assertEquals("Excalibur", invItem.displayName());
        assertEquals(5, invItem.enchantments().get("minecraft:sharpness"));
    }

    @Test
    void findRecentByPlayerReturnsOrderedResults() throws Exception {
        UUID player = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        snapshotRepo.insert(new InventorySnapshot(
                s1, player, CaptureReason.PRE_DEATH, 5_000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1));
        snapshotRepo.insert(new InventorySnapshot(
                s2, player, CaptureReason.POST_RESPAWN, 10_000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1));

        List<InventorySnapshot> results = snapshotRepo.findRecentByPlayer(player, 10);
        assertEquals(2, results.size());
        assertEquals(s2, results.get(0).id());
        assertEquals(s1, results.get(1).id());
    }

    @Test
    void countReturnsCorrectNumber() throws Exception {
        assertEquals(0, snapshotRepo.count());

        snapshotRepo.insert(createSnapshot(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(1, snapshotRepo.count());

        snapshotRepo.insert(createSnapshot(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(2, snapshotRepo.count());
    }

    @Test
    void insertIncidentAndFindById() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        snapshotRepo.insert(createSnapshot(snapshotId, player));
        incidentRepo.insert(createIncident(incidentId, player, snapshotId, "dedup-1"));

        Optional<Incident> found = incidentRepo.findById(incidentId);
        assertTrue(found.isPresent());
        Incident retrieved = found.get();
        assertEquals(IncidentType.PLAYER_DEATH, retrieved.type());
        assertEquals("ENTITY_ATTACK", retrieved.cause());
        assertEquals("minecraft:zombie", retrieved.killerEntityKey());
        assertTrue(retrieved.hasKillerPlayer());
        assertEquals("false", retrieved.metadataValue("keepInventory"));
    }

    @Test
    void duplicateDeduplicationKeyThrows() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        snapshotRepo.insert(createSnapshot(snapshotId, player));

        incidentRepo.insert(createIncident(UUID.randomUUID(), player, snapshotId, "same-key"));
        assertThrows(SQLException.class, () ->
                incidentRepo.insert(createIncident(UUID.randomUUID(), player, snapshotId, "same-key")));
    }

    @Test
    void updatePostEventSnapshot() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID postSnapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        snapshotRepo.insert(createSnapshot(snapshotId, player));
        incidentRepo.insert(createIncident(incidentId, player, snapshotId, "dedup-2"));

        snapshotRepo.insert(new InventorySnapshot(
                postSnapshotId, player, CaptureReason.POST_RESPAWN, 12_000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1));

        incidentRepo.updatePostEventSnapshot(incidentId, postSnapshotId);

        Optional<Incident> found = incidentRepo.findById(incidentId);
        assertTrue(found.isPresent());
        assertTrue(found.get().hasPostEventSnapshot());
        assertEquals(postSnapshotId, found.get().postEventSnapshotUuid());
    }

    @Test
    void findIncidentsByPlayerReturnsOrderedResults() throws Exception {
        UUID player = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        snapshotRepo.insert(createSnapshot(snapshotId, player));

        incidentRepo.insert(new Incident(
                UUID.randomUUID(), IncidentType.PLAYER_DEATH, player,
                5_000L, "world", new Coordinates(0, 0, 0),
                "", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), "key-1"));
        incidentRepo.insert(new Incident(
                UUID.randomUUID(), IncidentType.PLAYER_DEATH, player,
                10_000L, "world", new Coordinates(0, 0, 0),
                "", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), "key-2"));

        List<Incident> results = incidentRepo.findRecentByPlayer(player, 10);
        assertEquals(2, results.size());
        assertEquals(10_000L, results.get(0).occurredAt());
        assertEquals(5_000L, results.get(1).occurredAt());
    }

    @Test
    void incidentCountReturnsCorrectNumber() throws Exception {
        assertEquals(0, incidentRepo.count());

        UUID snapshotId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        snapshotRepo.insert(createSnapshot(snapshotId, player));
        incidentRepo.insert(createIncident(UUID.randomUUID(), player, snapshotId, "k1"));
        assertEquals(1, incidentRepo.count());
    }

    @Test
    void snapshotInsertWithItemsIsAtomic() throws Exception {
        UUID id = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        InventorySnapshot snapshot = new InventorySnapshot(
                id, player, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0,
                List.of(
                        new SnapshotItem("0", "minecraft:stone", 32, "data", "", 5, "", 0, 0, Map.of()),
                        new SnapshotItem("1", "minecraft:dirt", 64, "data2", "", 2, "", 0, 0, Map.of())
                ),
                List.of(), null, null, 1
        );

        snapshotRepo.insert(snapshot);

        Optional<InventorySnapshot> found = snapshotRepo.findById(id);
        assertTrue(found.isPresent());
        assertEquals(2, found.get().inventoryItems().size());
    }

    @Test
    void metadataWithSpecialCharactersRoundTrips() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        snapshotRepo.insert(createSnapshot(snapshotId, player));

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("dropsSummary", "minecraft:stone:32,minecraft:dirt:64");
        metadata.put("special=key", "value;with;semicolons");

        Incident incident = new Incident(
                incidentId, IncidentType.PLAYER_DEATH, player,
                1000L, "world", new Coordinates(0, 0, 0),
                "", null, "", snapshotId, null, IncidentStatus.OPEN,
                metadata, "dedup-special"
        );
        incidentRepo.insert(incident);

        Optional<Incident> found = incidentRepo.findById(incidentId);
        assertTrue(found.isPresent());
        assertEquals("minecraft:stone:32,minecraft:dirt:64", found.get().metadataValue("dropsSummary"));
        assertEquals("value;with;semicolons", found.get().metadataValue("special=key"));
    }
}
