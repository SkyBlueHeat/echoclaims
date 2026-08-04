package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimStoreTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private IncidentRepository incidentRepository;
    private InventorySnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("claims_test.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
    }

    private UUID seedSnapshotAndIncident(UUID playerUuid) throws java.sql.SQLException {
        UUID snapshotId = UUID.randomUUID();
        snapshotRepository.insert(new io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot(
                snapshotId, playerUuid,
                io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason.PRE_DEATH,
                10_000L, "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        ));

        UUID incidentId = UUID.randomUUID();
        incidentRepository.insert(new Incident(
                incidentId, IncidentType.PLAYER_DEATH, playerUuid,
                10_000L, "world", new Coordinates(0, 0, 0),
                "FALL", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), "dedup-" + UUID.randomUUID()
        ));
        return incidentId;
    }

    private Claim createDraftClaim(UUID incidentId, UUID playerUuid) {
        return new Claim(
                UUID.randomUUID(), "REF" + System.nanoTime() % 100000,
                incidentId, playerUuid,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "Test claim",
                System.currentTimeMillis(), 0L, 0L, 0, Map.of()
        );
    }

    @Test
    void createClaimPersistsClaimAndAudit() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);
        Claim claim = createDraftClaim(incidentId, player);

        ClaimAuditEntry audit = new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "Created", System.currentTimeMillis(), 0, Map.of()
        );

        claimStore.createClaim(claim, audit);

        Optional<Claim> found = claimStore.findClaimById(claim.id());
        assertTrue(found.isPresent());
        assertEquals(claim.publicReference(), found.get().publicReference());
        assertEquals(ClaimStatus.DRAFT, found.get().status());

        List<ClaimAuditEntry> entries = claimStore.findAuditEntries(claim.id());
        assertEquals(1, entries.size());
        assertEquals(ClaimAction.CREATED, entries.get(0).action());
    }

    @Test
    void findByPublicReferenceWorks() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);
        Claim claim = createDraftClaim(incidentId, player);

        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        ));

        Optional<Claim> found = claimStore.findClaimByPublicReference(claim.publicReference());
        assertTrue(found.isPresent());
        assertEquals(claim.id(), found.get().id());
    }

    @Test
    void findRecentClaimsByPlayerReturnsOrderedResults() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId1 = seedSnapshotAndIncident(player);
        UUID incidentId2 = seedSnapshotAndIncident(player);

        Claim claim1 = new Claim(
                UUID.randomUUID(), "REF001", incidentId1, player,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        );
        Claim claim2 = new Claim(
                UUID.randomUUID(), "REF002", incidentId2, player,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                20_000L, 0L, 0L, 0, Map.of()
        );

        claimStore.createClaim(claim1, new ClaimAuditEntry(
                UUID.randomUUID(), claim1.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", 10_000L, 0, Map.of()
        ));
        claimStore.createClaim(claim2, new ClaimAuditEntry(
                UUID.randomUUID(), claim2.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", 20_000L, 0, Map.of()
        ));

        List<Claim> claims = claimStore.findRecentClaimsByPlayer(player, 10);
        assertEquals(2, claims.size());
        assertEquals(20_000L, claims.get(0).createdAt());
        assertEquals(10_000L, claims.get(1).createdAt());
    }

    @Test
    void findOpenClaimsByIncidentReturnsOnlyOpen() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);

        Claim draft = new Claim(
                UUID.randomUUID(), "OPN001", incidentId, player,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        );
        Claim cancelled = new Claim(
                UUID.randomUUID(), "OPN002", incidentId, player,
                ClaimStatus.CANCELLED, ClaimSource.PLAYER_COMMAND, "",
                20_000L, 0L, 30_000L, 1, Map.of()
        );

        claimStore.createClaim(draft, new ClaimAuditEntry(
                UUID.randomUUID(), draft.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", 10_000L, 0, Map.of()
        ));
        claimStore.createClaim(cancelled, new ClaimAuditEntry(
                UUID.randomUUID(), cancelled.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", 20_000L, 0, Map.of()
        ));

        List<Claim> open = claimStore.findOpenClaimsByIncident(incidentId);
        assertEquals(1, open.size());
        assertEquals(draft.id(), open.get(0).id());
    }

    @Test
    void transitionClaimUpdatesStatusAndVersion() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);
        Claim claim = createDraftClaim(incidentId, player);

        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        ));

        long now = System.currentTimeMillis();
        boolean success = claimStore.transitionClaim(
                claim.id(), ClaimStatus.SUBMITTED, now, 0L, 0,
                new ClaimAuditEntry(
                        UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                        ClaimAction.SUBMITTED, "", now, 1, Map.of()
                )
        );

        assertTrue(success);

        Optional<Claim> updated = claimStore.findClaimById(claim.id());
        assertTrue(updated.isPresent());
        assertEquals(ClaimStatus.SUBMITTED, updated.get().status());
        assertEquals(1, updated.get().version());
        assertEquals(now, updated.get().submittedAt());

        List<ClaimAuditEntry> entries = claimStore.findAuditEntries(claim.id());
        assertEquals(2, entries.size());
    }

    @Test
    void transitionClaimFailsOnVersionMismatch() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);
        Claim claim = createDraftClaim(incidentId, player);

        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        ));

        boolean success = claimStore.transitionClaim(
                claim.id(), ClaimStatus.SUBMITTED, System.currentTimeMillis(), 0L, 99,
                new ClaimAuditEntry(
                        UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                        ClaimAction.SUBMITTED, "", System.currentTimeMillis(), 1, Map.of()
                )
        );

        assertFalse(success);

        Optional<Claim> unchanged = claimStore.findClaimById(claim.id());
        assertTrue(unchanged.isPresent());
        assertEquals(ClaimStatus.DRAFT, unchanged.get().status());
        assertEquals(0, unchanged.get().version());

        List<ClaimAuditEntry> entries = claimStore.findAuditEntries(claim.id());
        assertEquals(1, entries.size());
    }

    @Test
    void countMethodsWork() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);
        Claim claim = createDraftClaim(incidentId, player);

        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        ));

        assertEquals(1, claimStore.countClaims());
        assertEquals(1, claimStore.countClaimsByStatus(ClaimStatus.DRAFT));
        assertEquals(0, claimStore.countClaimsByStatus(ClaimStatus.SUBMITTED));
        assertEquals(1, claimStore.countClaimsByPlayerAndStatus(player, ClaimStatus.DRAFT));
        assertEquals(1, claimStore.countAuditEntries());
    }

    @Test
    void databaseRejectsDuplicateActiveClaimForSameIncidentAndPlayer() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);

        Claim claim1 = createDraftClaim(incidentId, player);
        claimStore.createClaim(claim1, new ClaimAuditEntry(
                UUID.randomUUID(), claim1.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        ));

        Claim claim2 = createDraftClaim(incidentId, player);
        try {
            claimStore.createClaim(claim2, new ClaimAuditEntry(
                    UUID.randomUUID(), claim2.id(), ClaimActorType.PLAYER, player,
                    ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
            ));
            org.junit.jupiter.api.Assertions.fail("Should have thrown due to unique active claim constraint");
        } catch (java.sql.SQLException expected) {
            assertTrue(expected.getMessage().contains("UNIQUE constraint failed"),
                    "Expected UNIQUE constraint failure, got: " + expected.getMessage());
        }

        assertEquals(1, claimStore.countClaims(), "Only one claim should exist");
        assertEquals(1, claimStore.countAuditEntries(), "Only one audit entry should exist");
    }

    @Test
    void cancelledClaimAllowsNewActiveClaimForSameIncident() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);

        Claim claim1 = createDraftClaim(incidentId, player);
        claimStore.createClaim(claim1, new ClaimAuditEntry(
                UUID.randomUUID(), claim1.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", 10_000L, 0, Map.of()
        ));

        claimStore.transitionClaim(
                claim1.id(), ClaimStatus.CANCELLED, 20_000L, 0L, 0,
                new ClaimAuditEntry(UUID.randomUUID(), claim1.id(), ClaimActorType.PLAYER, player,
                        ClaimAction.CANCELLED, "", 20_000L, 1, Map.of())
        );

        Claim claim2 = new Claim(
                UUID.randomUUID(), "REFCANCEL", incidentId, player,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                30_000L, 0L, 0L, 0, Map.of()
        );
        claimStore.createClaim(claim2, new ClaimAuditEntry(
                UUID.randomUUID(), claim2.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", 30_000L, 0, Map.of()
        ));

        assertEquals(2, claimStore.countClaims());
        assertEquals(1, claimStore.countClaimsByStatus(ClaimStatus.DRAFT));
        assertEquals(1, claimStore.countClaimsByStatus(ClaimStatus.CANCELLED));
    }

    @Test
    void differentPlayersCanClaimSameIncident() throws Exception {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player1);

        Claim claim1 = createDraftClaim(incidentId, player1);
        claimStore.createClaim(claim1, new ClaimAuditEntry(
                UUID.randomUUID(), claim1.id(), ClaimActorType.PLAYER, player1,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        ));

        Claim claim2 = new Claim(
                UUID.randomUUID(), "REFOTHER", incidentId, player2,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                System.currentTimeMillis(), 0L, 0L, 0, Map.of()
        );
        claimStore.createClaim(claim2, new ClaimAuditEntry(
                UUID.randomUUID(), claim2.id(), ClaimActorType.PLAYER, player2,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        ));

        assertEquals(2, claimStore.countClaims());
    }

    @Test
    void auditInsertionFailureRollsBackClaimCreation() throws Exception {
        UUID player = UUID.randomUUID();
        UUID incidentId = seedSnapshotAndIncident(player);
        Claim claim = createDraftClaim(incidentId, player);

        ClaimAuditEntry badAudit = new ClaimAuditEntry(
                UUID.randomUUID(), UUID.randomUUID(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", System.currentTimeMillis(), 0, Map.of()
        );

        try {
            claimStore.createClaim(claim, badAudit);
            org.junit.jupiter.api.Assertions.fail("Should have thrown due to FK violation on audit entry");
        } catch (java.sql.SQLException expected) {
        }

        Optional<Claim> found = claimStore.findClaimById(claim.id());
        assertTrue(found.isEmpty(), "Claim should not exist after rollback");
    }
}
