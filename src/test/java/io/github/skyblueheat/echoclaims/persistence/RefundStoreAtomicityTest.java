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
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundStoreAtomicityTest {

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
        databaseManager = new DatabaseManager(tempDir.resolve("refund_atomicity.db"));
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

    private ClaimAuditEntry claimAuditEntry(ClaimAction action) {
        return new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, action, "refund",
                System.currentTimeMillis(), 0, Map.of()
        );
    }

    private RefundItem makeItem(UUID refundId) {
        long now = System.currentTimeMillis();
        return new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 5, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );
    }

    // ─── 1. Duplicate refund creation rolls back entirely ───

    @Test
    void duplicateRefundCreationLeavesNoOrphanItems() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );
        RefundItem item = makeItem(refundId);

        refundStore.createRefund(refund, List.of(item),
                auditEntry(refundId, RefundAction.REFUND_CREATED),
                claimAuditEntry(ClaimAction.REFUND_CREATED));

        // Second attempt with same review_id should fail due to UNIQUE constraint
        RefundItem item2 = makeItem(refundId);
        assertThrows(SQLException.class, () ->
                refundStore.createRefund(refund, List.of(item2),
                        auditEntry(refundId, RefundAction.REFUND_CREATED),
                        claimAuditEntry(ClaimAction.REFUND_CREATED)));

        // Verify no orphan items from the failed second attempt
        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        assertEquals(1, items.size(), "Only the original item should exist");

        // Verify only one audit entry
        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(1, audits.size(), "Only one audit entry should exist");
    }

    // ─── 2. Refund creation with null items list still creates refund atomically ───

    @Test
    void refundCreationWithEmptyItemsSucceeds() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );

        refundStore.createRefund(refund, List.of(),
                auditEntry(refundId, RefundAction.REFUND_CREATED),
                claimAuditEntry(ClaimAction.REFUND_CREATED));

        Optional<Refund> found = refundRepository.findById(refundId);
        assertTrue(found.isPresent());
        assertEquals(RefundStatus.PENDING, found.get().status());

        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        assertEquals(0, items.size());
    }

    // ─── 3. Item delivery update with stale version does not create orphan audit ───

    @Test
    void itemDeliveryStaleVersionLeavesNoOrphanAudit() throws Exception {
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
                "snap:0", UUID.randomUUID(), "diamond", 5, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(item);

        RefundAuditEntry audit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_ITEM_DELIVERED, itemId, 5, "",
                now + 1000, Map.of()
        );

        boolean success = refundStore.updateItemDelivery(
                itemId, 5, RefundItemStatus.DELIVERED, "",
                now + 1000, 99, audit);
        assertFalse(success, "Stale version should fail");

        // No orphan audit entry should exist
        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(0, audits.size(), "No audit entry should exist on failed update");

        // Item should be unchanged
        RefundItem dbItem = itemRepository.findById(itemId).orElseThrow();
        assertEquals(0, dbItem.deliveredQuantity());
        assertEquals(RefundItemStatus.PENDING, dbItem.status());
    }

    // ─── 4. Complete refund with stale version does not create orphan audit ───

    @Test
    void completeRefundStaleVersionLeavesNoOrphanAudit() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        boolean success = refundStore.completeRefund(
                refundId, now + 5000, 99,
                auditEntry(refundId, RefundAction.REFUND_COMPLETED),
                claimAuditEntry(ClaimAction.REFUND_COMPLETED));
        assertFalse(success, "Stale version should fail");

        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(0, audits.size(), "No audit entry should exist on failed completion");

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.DELIVERING, dbRefund.status());
        assertEquals(0, dbRefund.completedAt());
    }

    // ─── 5. Fail refund with stale version does not create orphan audit ───

    @Test
    void failRefundStaleVersionLeavesNoOrphanAudit() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        boolean success = refundStore.failRefund(
                refundId, "test failure", 99,
                auditEntry(refundId, RefundAction.REFUND_FAILED),
                claimAuditEntry(ClaimAction.REFUND_FAILED));
        assertFalse(success, "Stale version should fail");

        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(0, audits.size(), "No audit entry should exist on failed fail");

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.DELIVERING, dbRefund.status());
    }

    // ─── 6. Retry refund with stale version does not create orphan audit ───

    @Test
    void retryRefundStaleVersionLeavesNoOrphanAudit() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.FAILED, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        boolean success = refundStore.retryRefund(
                refundId, RefundStatus.READY, 99,
                auditEntry(refundId, RefundAction.REFUND_RETRIED),
                claimAuditEntry(ClaimAction.REFUND_RETRIED));
        assertFalse(success, "Stale version should fail");

        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(0, audits.size(), "No audit entry should exist on failed retry");

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.FAILED, dbRefund.status());
    }

    // ─── 7. Start delivery with stale version does not create orphan audit ───

    @Test
    void startDeliveryStaleVersionLeavesNoOrphanAudit() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        boolean success = refundStore.startDelivery(
                refundId, 99,
                auditEntry(refundId, RefundAction.REFUND_EXECUTION_STARTED),
                claimAuditEntry(ClaimAction.REFUND_EXECUTION_STARTED));
        assertFalse(success, "Stale version should fail");

        List<RefundAuditEntry> audits = auditRepository.findByRefundId(refundId);
        assertEquals(0, audits.size(), "No audit entry should exist on failed start");

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.PENDING, dbRefund.status());
    }
}
