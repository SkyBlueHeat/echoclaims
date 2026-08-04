package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCreationServiceTest {

    @TempDir
    Path tempDir;

    private io.github.skyblueheat.echoclaims.persistence.DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimMetrics metrics;
    private ClaimCreationService service;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new io.github.skyblueheat.echoclaims.persistence.DatabaseManager(
                tempDir.resolve("creation_test.db"));
        databaseManager.initialize();
        claimStore = new io.github.skyblueheat.echoclaims.persistence.SqliteClaimStore(databaseManager);
        metrics = new ClaimMetrics();
        service = new ClaimCreationService(
                claimStore, new ClaimReferenceGenerator(), metrics);
    }

    private Incident seedIncident(UUID playerUuid) throws Exception {
        var snapshotRepo = new io.github.skyblueheat.echoclaims.persistence.SqliteInventorySnapshotRepository(databaseManager);
        var incidentRepo = new io.github.skyblueheat.echoclaims.persistence.SqliteIncidentRepository(databaseManager);

        UUID snapshotId = UUID.randomUUID();
        snapshotRepo.insert(new io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot(
                snapshotId, playerUuid,
                io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason.PRE_DEATH,
                10_000L, "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, java.util.List.of(), java.util.List.of(), null, null, 1
        ));

        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(
                incidentId, IncidentType.PLAYER_DEATH, playerUuid,
                10_000L, "world", new Coordinates(0, 0, 0),
                "FALL", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), "dedup-" + UUID.randomUUID()
        );
        incidentRepo.insert(incident);
        return incident;
    }

    @Test
    void createsClaimWithDraftStatusAndAuditEntry() throws Exception {
        UUID player = UUID.randomUUID();
        Incident incident = seedIncident(player);

        Claim claim = service.createClaim(incident, player, "Lost my diamonds");

        assertNotNull(claim);
        assertEquals(ClaimStatus.DRAFT, claim.status());
        assertEquals(ClaimSource.PLAYER_COMMAND, claim.source());
        assertEquals(incident.id(), claim.incidentId());
        assertEquals(player, claim.playerUuid());
        assertEquals("Lost my diamonds", claim.description());
        assertEquals(0, claim.version());
        assertTrue(claim.publicReference().length() == 8);

        Optional<Claim> found = claimStore.findClaimById(claim.id());
        assertTrue(found.isPresent());

        var auditEntries = claimStore.findAuditEntries(claim.id());
        assertEquals(1, auditEntries.size());
        assertEquals(ClaimAction.CREATED, auditEntries.get(0).action());

        assertEquals(1, metrics.claimsCreated());
    }

    @Test
    void createsClaimWithNullDescription() throws Exception {
        UUID player = UUID.randomUUID();
        Incident incident = seedIncident(player);

        Claim claim = service.createClaim(incident, player, null);
        assertEquals("", claim.description());
    }
}
