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
import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAction;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItemStatus;
import io.github.skyblueheat.echoclaims.domain.refund.RefundStatus;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
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

class RefundStoreTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private IncidentRepository incidentRepository;
    private InventorySnapshotRepository snapshotRepository;
    private RefundRepository refundRepository;
    private RefundItemRepository itemRepository;
    private RefundAuditRepository auditRepository;
    private RefundStore refundStore;

    private UUID playerUuid;
    private UUID claimId;
    private UUID reviewId;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("refund_store_test.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
        refundRepository = new SqliteRefundRepository(databaseManager);
        itemRepository = new SqliteRefundItemRepository(databaseManager);
        auditRepository = new SqliteRefundAuditRepository(databaseManager);
        refundStore = new SqliteRefundStore(databaseManager);

        seedClaimAndReview();
    }

    private void seedClaimAndReview() throws Exception {
        playerUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        snapshotRepository.insert(new InventorySnapshot(
                snapshotId, playerUuid, CaptureReason.PRE_DEATH,
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
        claimId = UUID.randomUUID();
        Claim claim = new Claim(
                claimId, "REF" + claimId.toString().substring(0, 8).toUpperCase(),
                incidentId, playerUuid, ClaimStatus.SUBMITTED, ClaimSource.PLAYER_COMMAND,
                "desc", 10_000L, 10_000L, 0L, 0, Map.of()
        );
        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER,
                claim.playerUuid(), ClaimAction.CREATED, "",
                System.currentTimeMillis(), 0, Map.of()
        ));

        reviewId = UUID.randomUUID();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                reviewId, claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        ClaimAuditEntry audit = new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.STAFF,
                staffUuid, ClaimAction.REVIEW_STARTED, "test",
                now, 1, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(), audit);
    }

    private RefundAuditEntry auditEntry(UUID refundId, RefundAction action) {
        return new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, action, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
    }

    private ClaimAuditEntry claimAuditEntry(UUID claimId, ClaimAction action) {
        return new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, action, "refund",
                System.currentTimeMillis(), 0, Map.of()
        );
    }

    @Test
    void createRefundInsertsRefundItemsAndAudit() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );

        RefundItem item1 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond_sword", 1, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );
        RefundItem item2 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "diamond_pickaxe", 2, 0,
                "base64data2", RefundItemStatus.PENDING, "", now, now, 0
        );

        RefundAuditEntry refundAudit = auditEntry(refundId, RefundAction.REFUND_CREATED);
        ClaimAuditEntry claimAudit = claimAuditEntry(claimId, ClaimAction.REFUND_CREATED);

        refundStore.createRefund(refund, List.of(item1, item2), refundAudit, claimAudit);

        Optional<Refund> found = refundRepository.findById(refundId);
        assertTrue(found.isPresent());
        assertEquals(RefundStatus.PENDING, found.get().status());

        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        assertEquals(2, items.size());

        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(1, audits.size());
        assertEquals(RefundAction.REFUND_CREATED, audits.get(0).action());
    }

    @Test
    void createRefundIsAtomicOnFailure() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );

        RefundItem item = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond_sword", 1, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );

        RefundAuditEntry refundAudit = auditEntry(refundId, RefundAction.REFUND_CREATED);
        ClaimAuditEntry claimAudit = claimAuditEntry(claimId, ClaimAction.REFUND_CREATED);

        refundStore.createRefund(refund, List.of(item), refundAudit, claimAudit);

        boolean threw = false;
        try {
            refundStore.createRefund(refund, List.of(item), refundAudit, claimAudit);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "Duplicate refund should fail");

        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        assertEquals(1, items.size(), "Original items should still be there");
    }

    @Test
    void updateItemDeliveryUpdatesItemAndAudit() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        UUID itemId = UUID.randomUUID();
        RefundItem item = new RefundItem(
                itemId, refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond_sword", 5, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(item);

        RefundAuditEntry audit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_PARTIALLY_DELIVERED, itemId, 3,
                "partial delivery", now + 1000, Map.of()
        );

        boolean success = refundStore.updateItemDelivery(
                itemId, 3, RefundItemStatus.PARTIALLY_DELIVERED, "",
                now + 1000, 0, audit
        );
        assertTrue(success);

        Optional<RefundItem> found = itemRepository.findById(itemId);
        assertTrue(found.isPresent());
        assertEquals(3, found.get().deliveredQuantity());
        assertEquals(RefundItemStatus.PARTIALLY_DELIVERED, found.get().status());
        assertEquals(1, found.get().version());

        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(1, audits.size());
        assertEquals(RefundAction.REFUND_PARTIALLY_DELIVERED, audits.get(0).action());
    }

    @Test
    void updateItemDeliveryFailsOnStaleVersion() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        UUID itemId = UUID.randomUUID();
        RefundItem item = new RefundItem(
                itemId, refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond_sword", 5, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(item);

        RefundAuditEntry audit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_ITEM_DELIVERED, itemId, 5,
                "full delivery", now + 1000, Map.of()
        );

        boolean success = refundStore.updateItemDelivery(
                itemId, 5, RefundItemStatus.DELIVERED, "",
                now + 1000, 99, audit
        );
        assertFalse(success, "Stale version should fail");
    }

    @Test
    void completeRefundUpdatesStatusAndCompletedAt() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        RefundAuditEntry refundAudit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_COMPLETED, null, 0,
                "all items delivered", now + 5000, Map.of()
        );
        ClaimAuditEntry claimAudit = claimAuditEntry(claimId, ClaimAction.REFUND_COMPLETED);

        boolean success = refundStore.completeRefund(refundId, now + 5000, 1, refundAudit, claimAudit);
        assertTrue(success);

        Optional<Refund> found = refundRepository.findById(refundId);
        assertTrue(found.isPresent());
        assertEquals(RefundStatus.COMPLETED, found.get().status());
        assertEquals(now + 5000, found.get().completedAt());
        assertEquals(2, found.get().version());
    }

    @Test
    void findByClaimIdReturnsRefund() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        Optional<Refund> found = refundRepository.findByClaimId(claimId);
        assertTrue(found.isPresent());
        assertEquals(refundId, found.get().id());
    }

    @Test
    void findByPlayerUuidAndStatusReturnsOnlyMatching() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        List<Refund> pending = refundRepository.findByPlayerUuidAndStatus(playerUuid, RefundStatus.PENDING, 10);
        assertEquals(1, pending.size());
        assertEquals(refundId, pending.get(0).id());
    }
}
