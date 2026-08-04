package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTransitionServiceTest {

    @TempDir
    Path tempDir;

    private ClaimStore claimStore;
    private ClaimMetrics metrics;
    private ClaimTransitionService service;
    private UUID player;
    private UUID incidentId;
    private Claim claim;

    @BeforeEach
    void setUp() throws Exception {
        var databaseManager = new io.github.skyblueheat.echoclaims.persistence.DatabaseManager(
                tempDir.resolve("transition_test.db"));
        databaseManager.initialize();
        claimStore = new io.github.skyblueheat.echoclaims.persistence.SqliteClaimStore(databaseManager);
        metrics = new ClaimMetrics();
        service = new ClaimTransitionService(claimStore, metrics);

        player = UUID.randomUUID();

        var snapshotRepo = new io.github.skyblueheat.echoclaims.persistence.SqliteInventorySnapshotRepository(databaseManager);
        var incidentRepo = new io.github.skyblueheat.echoclaims.persistence.SqliteIncidentRepository(databaseManager);

        UUID snapshotId = UUID.randomUUID();
        snapshotRepo.insert(new io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot(
                snapshotId, player,
                io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason.PRE_DEATH,
                10_000L, "world", new io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates(0, 0, 0),
                "survival", 20, 20, 0, 0, java.util.List.of(), java.util.List.of(), null, null, 1
        ));

        incidentId = UUID.randomUUID();
        incidentRepo.insert(new io.github.skyblueheat.echoclaims.domain.incident.Incident(
                incidentId, io.github.skyblueheat.echoclaims.domain.incident.IncidentType.PLAYER_DEATH,
                player, 10_000L, "world",
                new io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates(0, 0, 0),
                "FALL", null, "", snapshotId, null,
                io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus.OPEN,
                java.util.Map.of(), "dedup-" + UUID.randomUUID()
        ));

        claim = new Claim(
                UUID.randomUUID(), "TRREF01", incidentId, player,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        );

        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                ClaimAction.CREATED, "", 10_000L, 0, Map.of()
        ));
    }

    @Test
    void transitionDraftToSubmittedSucceeds() {
        var result = service.transition(claim, ClaimStatus.SUBMITTED, player, "");
        assertTrue(result.isSuccess());
        assertEquals(ClaimStatus.SUBMITTED, result.newStatus().orElseThrow());
        assertEquals(1, result.newVersion());
        assertEquals(1, metrics.claimsSubmitted());
    }

    @Test
    void transitionDraftToCancelledSucceeds() {
        var result = service.transition(claim, ClaimStatus.CANCELLED, player, "");
        assertTrue(result.isSuccess());
        assertEquals(ClaimStatus.CANCELLED, result.newStatus().orElseThrow());
        assertEquals(1, metrics.claimsCancelled());
    }

    @Test
    void transitionSameStatusIsRejected() {
        var result = service.transition(claim, ClaimStatus.DRAFT, player, "");
        assertFalse(result.isSuccess());
        assertEquals(1, metrics.claimsRejected());
    }

    @Test
    void transitionFromCancelledIsRejected() throws Exception {
        claimStore.transitionClaim(
                claim.id(), ClaimStatus.CANCELLED, System.currentTimeMillis(), 0L, 0,
                new ClaimAuditEntry(UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                        ClaimAction.CANCELLED, "", System.currentTimeMillis(), 1, Map.of())
        );
        Optional<Claim> cancelled = claimStore.findClaimById(claim.id());

        var result = service.transition(cancelled.get(), ClaimStatus.SUBMITTED, player, "");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void transitionWithStaleVersionFailsAsConcurrencyConflict() throws Exception {
        claimStore.transitionClaim(
                claim.id(), ClaimStatus.SUBMITTED, System.currentTimeMillis(), 0L, 0,
                new ClaimAuditEntry(UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER, player,
                        ClaimAction.SUBMITTED, "", System.currentTimeMillis(), 1, Map.of())
        );

        var result = service.transition(claim, ClaimStatus.CANCELLED, player, "");
        assertFalse(result.isSuccess());
        assertTrue(result.isConcurrencyConflict());
        assertEquals(1, metrics.concurrencyConflicts());
    }

    @Test
    void transitionPersistsAuditEntry() {
        service.transition(claim, ClaimStatus.SUBMITTED, player, "");

        try {
            List<ClaimAuditEntry> entries = claimStore.findAuditEntries(claim.id());
            assertEquals(2, entries.size());
            assertEquals(ClaimAction.SUBMITTED, entries.get(1).action());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
