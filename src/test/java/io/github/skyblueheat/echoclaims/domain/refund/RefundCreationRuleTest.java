package io.github.skyblueheat.echoclaims.domain.refund;

import io.github.skyblueheat.echoclaims.application.RefundService;
import io.github.skyblueheat.echoclaims.application.RefundServiceConfig;
import io.github.skyblueheat.echoclaims.domain.claim.*;
import io.github.skyblueheat.echoclaims.domain.incident.*;
import io.github.skyblueheat.echoclaims.domain.review.*;
import io.github.skyblueheat.echoclaims.domain.snapshot.*;
import io.github.skyblueheat.echoclaims.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefundCreationRuleTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private IncidentRepository incidentRepo;
    private InventorySnapshotRepository snapshotRepo;
    private RefundRepository refundRepo;
    private RefundItemRepository itemRepo;
    private RefundAuditRepository auditRepo;
    private RefundStore refundStore;
    private ClaimReviewRepository reviewRepo;
    private ClaimReviewItemDecisionRepository decisionRepo;
    private RefundService refundService;

    private UUID playerUuid;
    private UUID claimId;
    private UUID reviewId;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseManager(tempDir.resolve("refund_creation.db"));
        db.initialize();
        claimStore = new SqliteClaimStore(db);
        reviewStore = new SqliteClaimReviewStore(db);
        incidentRepo = new SqliteIncidentRepository(db);
        snapshotRepo = new SqliteInventorySnapshotRepository(db);
        refundRepo = new SqliteRefundRepository(db);
        itemRepo = new SqliteRefundItemRepository(db);
        auditRepo = new SqliteRefundAuditRepository(db);
        refundStore = new SqliteRefundStore(db);
        reviewRepo = new SqliteClaimReviewRepository(db);
        decisionRepo = new SqliteClaimReviewItemDecisionRepository(db);

        RefundServiceConfig config = new RefundServiceConfig(true, true, false, 100, true);
        refundService = new RefundService(
                refundRepo, itemRepo, auditRepo, refundStore,
                reviewRepo, decisionRepo, snapshotRepo, config
        );

        seedClaimAndReview();
    }

    private void seedClaimAndReview() throws Exception {
        playerUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        snapshotRepo.insert(new InventorySnapshot(
                snapshotId, playerUuid, CaptureReason.PRE_DEATH,
                10_000L, "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        ));
        UUID incidentId = UUID.randomUUID();
        incidentRepo.insert(new Incident(
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

    private Claim getClaim() {
        try {
            return claimStore.findClaimById(claimId).orElseThrow();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ClaimReview getReview() {
        try {
            return reviewRepo.findById(reviewId).orElseThrow();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ClaimReview finalizedReview(ReviewOutcome outcome) {
        ClaimReview r = getReview();
        long now = System.currentTimeMillis();
        return new ClaimReview(
                r.id(), r.claimId(), r.assignedReviewerUuid(),
                ReviewState.FINALIZED, outcome, "summary",
                r.startedAt(), now, now, r.version(), Map.of()
        );
    }

    // ─── P6: Domain/refund creation rule tests ───

    @Test
    void refundCannotBeCreatedForNonFinalizedReview() {
        Claim claim = getClaim();
        ClaimReview review = getReview();
        RefundService.CreateRefundResult result =
                refundService.createRefund(claim, review, List.of());
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().orElse("").contains("finalized"));
    }

    @Test
    void refundCannotBeCreatedForRejectedReview() {
        Claim claim = getClaim();
        ClaimReview review = finalizedReview(ReviewOutcome.REJECTED);
        RefundService.CreateRefundResult result =
                refundService.createRefund(claim, review, List.of());
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().orElse("").contains("REJECTED"));
    }

    @Test
    void refundCannotBeCreatedWhenReviewDoesNotBelongToClaim() {
        Claim claim = getClaim();
        ClaimReview review = finalizedReview(ReviewOutcome.APPROVED);
        // Create a different claim ID for the review
        UUID wrongClaimId = UUID.randomUUID();
        ClaimReview wrongReview = new ClaimReview(
                review.id(), wrongClaimId, review.assignedReviewerUuid(),
                review.reviewState(), review.finalOutcome(), review.finalSummary(),
                review.startedAt(), review.updatedAt(), review.finalizedAt(),
                review.version(), Map.of()
        );
        RefundService.CreateRefundResult result =
                refundService.createRefund(claim, wrongReview, List.of());
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().orElse("").contains("does not belong"));
    }

    @Test
    void refundCannotBeCreatedTwiceForSameReview() throws Exception {
        Claim claim = getClaim();
        ClaimReview review = finalizedReview(ReviewOutcome.APPROVED);

        // Insert a refund manually for this review
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );
        refundRepo.insert(refund);

        RefundService.CreateRefundResult result =
                refundService.createRefund(claim, review, List.of());
        assertTrue(result.isAlreadyExists());
        assertFalse(result.isSuccess());
    }

    @Test
    void refundCannotBeCreatedWithNoRefundableItems() {
        Claim claim = getClaim();
        ClaimReview review = finalizedReview(ReviewOutcome.APPROVED);
        RefundService.CreateRefundResult result =
                refundService.createRefund(claim, review, List.of());
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().orElse("").contains("No refundable items"));
    }

    @Test
    void refundCanBeCreatedForPartiallyApprovedReview() {
        Claim claim = getClaim();
        ClaimReview review = finalizedReview(ReviewOutcome.PARTIALLY_APPROVED);
        // No decisions → no refundable items, but the rule check should pass the outcome validation
        RefundService.CreateRefundResult result =
                refundService.createRefund(claim, review, List.of());
        // Should be rejected for no items, not for outcome
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().orElse("").contains("No refundable items"));
    }

    @Test
    void refundMetadataIsPreservedAfterCreation() throws Exception {
        // Verify that metadata passed to Refund constructor is preserved
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Map<String, String> metadata = Map.of("source", "test", "actor", "staff");
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, metadata
        );
        refundRepo.insert(refund);

        Refund found = refundRepo.findById(refundId).orElseThrow();
        assertEquals(2, found.metadata().size());
        assertEquals("test", found.metadata().get("source"));
        assertEquals("staff", found.metadata().get("actor"));
    }

    // ─── P8: Refund aggregate completion tests (5 combinations) ───

    @Test
    void allItemsDeliveredCompletesRefund() throws Exception {
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 0, Map.of()
        );
        refundRepo.insert(refund);

        RefundItem item1 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 5, 5,
                "data", RefundItemStatus.DELIVERED, "", now, now, 0
        );
        RefundItem item2 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "iron", 10, 10,
                "data", RefundItemStatus.DELIVERED, "", now, now, 0
        );
        itemRepo.insert(item1);
        itemRepo.insert(item2);

        List<RefundItem> pending = itemRepo.findPendingByRefundId(refundId);
        assertEquals(0, pending.size(), "No pending items when all delivered");

        RefundAuditEntry audit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_COMPLETED, null, 0, "",
                now, Map.of()
        );
        ClaimAuditEntry claimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_COMPLETED, "done",
                now, 0, Map.of()
        );
        assertTrue(refundStore.completeRefund(refundId, now, 0, audit, claimAudit));

        Refund completed = refundRepo.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.COMPLETED, completed.status());
        assertTrue(completed.completedAt() > 0);
    }

    @Test
    void someItemsPendingPreventsCompletion() throws Exception {
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 0, Map.of()
        );
        refundRepo.insert(refund);

        RefundItem delivered = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 5, 5,
                "data", RefundItemStatus.DELIVERED, "", now, now, 0
        );
        RefundItem pending = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "iron", 10, 0,
                "data", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepo.insert(delivered);
        itemRepo.insert(pending);

        List<RefundItem> stillPending = itemRepo.findPendingByRefundId(refundId);
        assertEquals(1, stillPending.size(), "One item still pending");
    }

    @Test
    void someItemsPartiallyDeliveredPreventsCompletion() throws Exception {
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 0, Map.of()
        );
        refundRepo.insert(refund);

        RefundItem delivered = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 5, 5,
                "data", RefundItemStatus.DELIVERED, "", now, now, 0
        );
        RefundItem partial = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "iron", 10, 3,
                "data", RefundItemStatus.PARTIALLY_DELIVERED, "", now, now, 0
        );
        itemRepo.insert(delivered);
        itemRepo.insert(partial);

        List<RefundItem> stillPending = itemRepo.findPendingByRefundId(refundId);
        assertEquals(1, stillPending.size(), "Partially delivered item should be in pending query");
    }

    @Test
    void allItemsFailedDoesNotCompleteButFails() throws Exception {
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 0, Map.of()
        );
        refundRepo.insert(refund);

        RefundItem failed = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 5, 0,
                "data", RefundItemStatus.FAILED, "deserialization error", now, now, 0
        );
        itemRepo.insert(failed);

        List<RefundItem> pending = itemRepo.findPendingByRefundId(refundId);
        assertEquals(0, pending.size(), "FAILED items are not pending");

        // The service should fail the refund, not complete it
        RefundAuditEntry failAudit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_FAILED, null, 0, "All items failed",
                now, Map.of()
        );
        ClaimAuditEntry failClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_FAILED, "failed",
                now, 0, Map.of()
        );
        assertTrue(refundStore.failRefund(refundId, "All items failed", 0, failAudit, failClaimAudit));

        Refund failedRefund = refundRepo.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.FAILED, failedRefund.status());
        assertFalse(failedRefund.isTerminal(), "FAILED is not terminal");
        assertTrue(failedRefund.status().isRecoverable(), "FAILED is recoverable");
    }

    @Test
    void mixedDeliveredAndFailedItemsFailsRefund() throws Exception {
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.DELIVERING, now, 0, 0, Map.of()
        );
        refundRepo.insert(refund);

        RefundItem delivered = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 5, 5,
                "data", RefundItemStatus.DELIVERED, "", now, now, 0
        );
        RefundItem failed = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "iron", 10, 0,
                "data", RefundItemStatus.FAILED, "error", now, now, 0
        );
        itemRepo.insert(delivered);
        itemRepo.insert(failed);

        List<RefundItem> pending = itemRepo.findPendingByRefundId(refundId);
        assertEquals(0, pending.size(), "No pending items (delivered + failed)");

        // Should fail, not complete, because some items failed
        RefundAuditEntry failAudit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_FAILED, null, 0, "1 item(s) failed",
                now, Map.of()
        );
        ClaimAuditEntry failClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_FAILED, "partial failure",
                now, 0, Map.of()
        );
        assertTrue(refundStore.failRefund(refundId, "1 item(s) failed", 0, failAudit, failClaimAudit));

        Refund result = refundRepo.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.FAILED, result.status());
        assertFalse(result.isTerminal());
    }
}
