package io.github.skyblueheat.echoclaims.application;

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
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewStore;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.IncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundAuditRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundItemRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteIncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteInventorySnapshotRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundAuditRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundItemRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private IncidentRepository incidentRepository;
    private RefundRepository refundRepository;
    private RefundItemRepository itemRepository;
    private RefundAuditRepository auditRepository;
    private RefundStore refundStore;
    private ClaimReviewRepository reviewRepository;
    private ClaimReviewItemDecisionRepository decisionRepository;
    private InventorySnapshotRepository snapshotRepository;
    private RefundService refundService;

    private UUID playerUuid;
    private UUID claimId;
    private UUID reviewId;
    private UUID refundId;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("refund_service_test.db"));
        databaseManager.initialize();

        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
        refundRepository = new SqliteRefundRepository(databaseManager);
        itemRepository = new SqliteRefundItemRepository(databaseManager);
        auditRepository = new SqliteRefundAuditRepository(databaseManager);
        refundStore = new SqliteRefundStore(databaseManager);
        reviewRepository = new SqliteClaimReviewRepository(databaseManager);
        decisionRepository = new SqliteClaimReviewItemDecisionRepository(databaseManager);

        RefundServiceConfig config = new RefundServiceConfig(
                true, true, false, 100, true
        );
        refundService = new RefundService(
                refundRepository, itemRepository, auditRepository, refundStore,
                reviewRepository, decisionRepository, snapshotRepository, config
        );

        seedClaimAndReview();
    }

    private void seedClaimAndReview() throws Exception {
        playerUuid = UUID.randomUUID();
        claimId = UUID.randomUUID();
        reviewId = UUID.randomUUID();
        seedClaimAndReviewPair(claimId, reviewId);
    }

    private UUID seedClaimAndReviewPair(UUID claimId, UUID reviewId) throws Exception {
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
        return reviewId;
    }

    private void seedRefundWithItem(int refundableQty, int deliveredQty,
                                     RefundItemStatus itemStatus) throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        UUID itemId = UUID.randomUUID();
        RefundItem item = new RefundItem(
                itemId, refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", refundableQty, deliveredQty,
                "base64data", itemStatus, "", now, now, 0
        );
        itemRepository.insert(item);
    }

    private RefundItem getFirstItem() throws Exception {
        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        return items.get(0);
    }

    private static final class MockDeliveryAdapter implements RefundDeliveryAdapter {
        private final int deliverQuantity;
        private final boolean fail;
        private final String failReason;

        MockDeliveryAdapter(int deliverQuantity) {
            this(deliverQuantity, false, "");
        }

        MockDeliveryAdapter(int deliverQuantity, boolean fail, String failReason) {
            this.deliverQuantity = deliverQuantity;
            this.fail = fail;
            this.failReason = failReason;
        }

        @Override
        public ItemDeliveryResult deliverItem(RefundItem item) {
            if (fail) {
                return ItemDeliveryResult.failure(failReason);
            }
            return ItemDeliveryResult.success(deliverQuantity);
        }
    }

    // ─── Partial Delivery Correctness ───

    @Test
    void partialDeliveryFirstExecutionDeliversPartialQuantity() throws Exception {
        seedRefundWithItem(64, 0, RefundItemStatus.PENDING);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(20));

        assertTrue(result.isPartialDelivery());
        assertEquals(20, result.totalDelivered());

        RefundItem item = getFirstItem();
        assertEquals(20, item.deliveredQuantity());
        assertEquals(44, item.remainingQuantity());
        assertEquals(RefundItemStatus.PARTIALLY_DELIVERED, item.status());

        Refund refund = refundRepository.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.READY, refund.status());
    }

    @Test
    void partialDeliverySecondExecutionDeliversRemaining() throws Exception {
        seedRefundWithItem(64, 0, RefundItemStatus.PENDING);

        refundService.executeRefund(claimId, null, new MockDeliveryAdapter(20));

        RefundService.ExecuteRefundResult result2 =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(44));

        assertTrue(result2.isSuccess());
        assertEquals(RefundStatus.COMPLETED, result2.refund().orElseThrow().status());

        RefundItem item = getFirstItem();
        assertEquals(64, item.deliveredQuantity());
        assertEquals(0, item.remainingQuantity());
        assertEquals(RefundItemStatus.DELIVERED, item.status());
    }

    @Test
    void completedRefundThirdExecutionDoesNotDeliverAgain() throws Exception {
        seedRefundWithItem(64, 0, RefundItemStatus.PENDING);

        refundService.executeRefund(claimId, null, new MockDeliveryAdapter(20));
        refundService.executeRefund(claimId, null, new MockDeliveryAdapter(44));

        RefundService.ExecuteRefundResult result3 =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(64));

        assertTrue(result3.isAlreadyCompleted());

        RefundItem item = getFirstItem();
        assertEquals(64, item.deliveredQuantity());
        assertEquals(0, item.remainingQuantity());
        assertEquals(RefundItemStatus.DELIVERED, item.status());
    }

    @Test
    void partiallyDeliveredItemIsIncludedInPendingQuery() throws Exception {
        seedRefundWithItem(64, 20, RefundItemStatus.PARTIALLY_DELIVERED);

        List<RefundItem> pending = itemRepository.findPendingByRefundId(refundId);
        assertEquals(1, pending.size());
        assertEquals(RefundItemStatus.PARTIALLY_DELIVERED, pending.get(0).status());
    }

    @Test
    void deliveredQuantityNeverExceedsRefundableQuantity() throws Exception {
        seedRefundWithItem(10, 0, RefundItemStatus.PENDING);

        refundService.executeRefund(claimId, null, new MockDeliveryAdapter(10));

        RefundItem item = getFirstItem();
        assertEquals(10, item.deliveredQuantity());
        assertEquals(0, item.remainingQuantity());
        assertTrue(item.deliveredQuantity() <= item.refundableQuantity());
    }

    @Test
    void multipleItemsPartialDeliveryDoesNotCompleteRefund() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        RefundItem item1 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 32, 0,
                "base64data1", RefundItemStatus.PENDING, "", now, now, 0
        );
        RefundItem item2 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "diamond_sword", 1, 0,
                "base64data2", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(item1);
        itemRepository.insert(item2);

        RefundDeliveryAdapter adapter = new RefundDeliveryAdapter() {
            private final AtomicInteger callCount = new AtomicInteger(0);

            @Override
            public ItemDeliveryResult deliverItem(RefundItem item) {
                int call = callCount.incrementAndGet();
                if (call == 1) {
                    return ItemDeliveryResult.success(32);
                } else {
                    return ItemDeliveryResult.success(0);
                }
            }
        };

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, adapter);

        assertTrue(result.isPartialDelivery() || result.isSuccess());

        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        RefundItem dbItem1 = items.stream().filter(i -> i.evidenceItemReference().equals("snap:0")).findFirst().orElseThrow();
        RefundItem dbItem2 = items.stream().filter(i -> i.evidenceItemReference().equals("snap:1")).findFirst().orElseThrow();

        assertEquals(RefundItemStatus.DELIVERED, dbItem1.status());
        assertEquals(32, dbItem1.deliveredQuantity());

        assertEquals(RefundItemStatus.PENDING, dbItem2.status());
        assertEquals(0, dbItem2.deliveredQuantity());

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertFalse(dbRefund.isCompleted(), "Refund must not be COMPLETED when item2 is still pending");
    }

    // ─── Completed Refund Execution ───

    @Test
    void completedRefundExecutionReturnsAlreadyCompleted() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.COMPLETED, now, now + 5000, 2, Map.of()
        );
        refundRepository.insert(refund);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(1));

        assertTrue(result.isAlreadyCompleted());
    }

    // ─── Failed Refund Execution ───

    @Test
    void failedRefundExecutionRejectsWithRetryMessage() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.FAILED, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(1));

        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().isPresent());
        assertTrue(result.rejectionReason().get().contains("FAILED"));
    }

    @Test
    void failedRefundIsNotTerminal() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.FAILED, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertFalse(dbRefund.isTerminal());
        assertTrue(dbRefund.isFailed());
        assertFalse(dbRefund.isCompleted());
    }

    // ─── Retry Refund ───

    @Test
    void retryFailedRefundTransitionsToReady() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.FAILED, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        RefundService.RetryRefundResult result =
                refundService.retryRefund(claimId, null);

        assertTrue(result.isSuccess());
        assertEquals(RefundStatus.READY, result.refund().orElseThrow().status());
    }

    @Test
    void retryNonFailedRefundRejected() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        RefundService.RetryRefundResult result =
                refundService.retryRefund(claimId, null);

        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void retryWhenDisabledRejected() throws Exception {
        RefundServiceConfig disabledConfig = new RefundServiceConfig(
                true, true, false, 100, false
        );
        RefundService serviceWithRetryDisabled = new RefundService(
                refundRepository, itemRepository, auditRepository, refundStore,
                reviewRepository, decisionRepository, snapshotRepository, disabledConfig
        );

        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.FAILED, now, 0, 1, Map.of()
        );
        refundRepository.insert(refund);

        RefundService.RetryRefundResult result =
                serviceWithRetryDisabled.retryRefund(claimId, null);

        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().isPresent());
        assertTrue(result.rejectionReason().get().contains("disabled"));
    }

    // ─── Find Pending Refunds ───

    @Test
    void findPendingRefundsReturnsOnlyPendingAndReady() throws Exception {
        long now = System.currentTimeMillis();

        UUID claim1Id = UUID.randomUUID();
        UUID review1Id = seedClaimAndReviewPair(claim1Id, UUID.randomUUID());
        refundRepository.insert(new Refund(
                UUID.randomUUID(), claim1Id, review1Id, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        ));

        UUID claim2Id = UUID.randomUUID();
        UUID review2Id = seedClaimAndReviewPair(claim2Id, UUID.randomUUID());
        refundRepository.insert(new Refund(
                UUID.randomUUID(), claim2Id, review2Id, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        ));

        UUID claim3Id = UUID.randomUUID();
        UUID review3Id = seedClaimAndReviewPair(claim3Id, UUID.randomUUID());
        refundRepository.insert(new Refund(
                UUID.randomUUID(), claim3Id, review3Id, playerUuid,
                RefundStatus.COMPLETED, now, now + 1000, 1, Map.of()
        ));

        List<Refund> pending = refundService.findPendingRefundsForPlayer(playerUuid);
        assertEquals(2, pending.size());
        assertTrue(pending.stream().allMatch(r -> !r.isCompleted()));
    }

    // ─── No Refund Found ───

    @Test
    void executeRefundWithNoRefundReturnsRejected() {
        UUID randomClaimId = UUID.randomUUID();
        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(randomClaimId, null, new MockDeliveryAdapter(1));

        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().isPresent());
        assertTrue(result.rejectionReason().get().contains("No refund"));
    }

    // ─── Find Refund By Claim ───

    @Test
    void findRefundByClaimIdReturnsRefundWithItemsAndAudit() throws Exception {
        seedRefundWithItem(10, 0, RefundItemStatus.PENDING);

        Optional<RefundService.RefundDetail> detail = refundService.findRefundByClaimId(claimId);

        assertTrue(detail.isPresent());
        assertEquals(1, detail.get().items().size());
        assertEquals(refundId, detail.get().refund().id());
    }

    @Test
    void findRefundByClaimIdReturnsEmptyWhenNoRefund() {
        Optional<RefundService.RefundDetail> detail = refundService.findRefundByClaimId(UUID.randomUUID());
        assertTrue(detail.isEmpty());
    }

    // ─── Aggregate Completion ───

    @Test
    void refundCompletesOnlyWhenAllItemsDelivered() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        RefundItem item1 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 10, 0,
                "base64data1", RefundItemStatus.PENDING, "", now, now, 0
        );
        RefundItem item2 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "diamond", 5, 0,
                "base64data2", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(item1);
        itemRepository.insert(item2);

        RefundDeliveryAdapter adapter = new RefundDeliveryAdapter() {
            private final AtomicInteger call = new AtomicInteger(0);

            @Override
            public ItemDeliveryResult deliverItem(RefundItem item) {
                int c = call.incrementAndGet();
                if (c == 1) {
                    return ItemDeliveryResult.success(10);
                }
                return ItemDeliveryResult.success(0);
            }
        };

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, adapter);

        assertTrue(result.isPartialDelivery() || result.isSuccess());

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertFalse(dbRefund.isCompleted(), "Refund must not complete when item2 is still pending");

        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        long deliveredCount = items.stream().filter(i -> i.status() == RefundItemStatus.DELIVERED).count();
        long pendingCount = items.stream().filter(i -> i.status() == RefundItemStatus.PENDING).count();
        assertEquals(1, deliveredCount);
        assertEquals(1, pendingCount);
    }

    @Test
    void refundCompletesWhenAllItemsFullyDelivered() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        RefundItem item1 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 10, 0,
                "base64data1", RefundItemStatus.PENDING, "", now, now, 0
        );
        RefundItem item2 = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "diamond", 5, 0,
                "base64data2", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(item1);
        itemRepository.insert(item2);

        RefundDeliveryAdapter adapter = new RefundDeliveryAdapter() {
            private final AtomicInteger call = new AtomicInteger(0);

            @Override
            public ItemDeliveryResult deliverItem(RefundItem item) {
                int c = call.incrementAndGet();
                if (c == 1) return ItemDeliveryResult.success(10);
                if (c == 2) return ItemDeliveryResult.success(5);
                return ItemDeliveryResult.success(0);
            }
        };

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, adapter);

        assertTrue(result.isSuccess());
        assertEquals(RefundStatus.COMPLETED, result.refund().orElseThrow().status());

        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        assertTrue(items.stream().allMatch(i -> i.status() == RefundItemStatus.DELIVERED));
        assertTrue(items.stream().allMatch(i -> i.deliveredQuantity() == i.refundableQuantity()));
    }

    // ─── Item Failure ───

    @Test
    void itemDeliveryFailureMarksItemFailedAndRefundFailed() throws Exception {
        seedRefundWithItem(10, 0, RefundItemStatus.PENDING);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null,
                        new MockDeliveryAdapter(0, true, "Deserialization failed"));

        assertTrue(result.isFailed() || result.isPartialDelivery() || !result.isSuccess());

        RefundItem item = getFirstItem();
        assertEquals(RefundItemStatus.FAILED, item.status());
        assertTrue(item.failureReason().contains("Deserialization"));
    }

    // ═══ P4: Inventory Delivery Test Matrix ═══

    @Test
    void emptyInventoryEverythingFits() throws Exception {
        seedRefundWithItem(64, 0, RefundItemStatus.PENDING);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(64));

        assertTrue(result.isSuccess());
        RefundItem item = getFirstItem();
        assertEquals(64, item.deliveredQuantity());
        assertEquals(0, item.remainingQuantity());
        assertEquals(RefundItemStatus.DELIVERED, item.status());
    }

    @Test
    void fullInventoryNothingFitsItemRemainsOutstanding() throws Exception {
        seedRefundWithItem(64, 0, RefundItemStatus.PENDING);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(0));

        assertTrue(result.isPartialDelivery() || !result.isSuccess());
        RefundItem item = getFirstItem();
        assertEquals(0, item.deliveredQuantity());
        assertEquals(64, item.remainingQuantity());
        assertEquals(RefundItemStatus.PENDING, item.status());

        Refund refund = refundRepository.findById(refundId).orElseThrow();
        assertFalse(refund.isCompleted(), "Refund must not be COMPLETED");
    }

    @Test
    void partiallyFullInventoryOnlyAvailableCapacityDelivered() throws Exception {
        seedRefundWithItem(64, 0, RefundItemStatus.PENDING);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(30));

        assertTrue(result.isPartialDelivery());
        RefundItem item = getFirstItem();
        assertEquals(30, item.deliveredQuantity());
        assertEquals(34, item.remainingQuantity());
        assertEquals(RefundItemStatus.PARTIALLY_DELIVERED, item.status());
    }

    @Test
    void existingCompatibleStackMergesCorrectly() throws Exception {
        seedRefundWithItem(20, 0, RefundItemStatus.PENDING);

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(14));

        assertTrue(result.isPartialDelivery());
        RefundItem item = getFirstItem();
        assertEquals(14, item.deliveredQuantity());
        assertEquals(6, item.remainingQuantity());
    }

    @Test
    void multipleItemsOneFitsOneDoesNot() throws Exception {
        long now = System.currentTimeMillis();
        refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        RefundItem diamondItem = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 32, 0,
                "base64diamond", RefundItemStatus.PENDING, "", now, now, 0
        );
        RefundItem swordItem = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:1", UUID.randomUUID(), "diamond_sword", 1, 0,
                "base64sword", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(diamondItem);
        itemRepository.insert(swordItem);

        RefundDeliveryAdapter adapter = new RefundDeliveryAdapter() {
            private final AtomicInteger call = new AtomicInteger(0);

            @Override
            public ItemDeliveryResult deliverItem(RefundItem item) {
                int c = call.incrementAndGet();
                if (c == 1) {
                    return ItemDeliveryResult.success(32);
                }
                return ItemDeliveryResult.success(0);
            }
        };

        RefundService.ExecuteRefundResult result =
                refundService.executeRefund(claimId, null, adapter);

        assertFalse(result.isSuccess() && !result.isPartialDelivery(),
                "Refund must not be fully completed when sword not delivered");

        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        RefundItem dbDiamond = items.stream().filter(i -> i.evidenceItemReference().equals("snap:0")).findFirst().orElseThrow();
        RefundItem dbSword = items.stream().filter(i -> i.evidenceItemReference().equals("snap:1")).findFirst().orElseThrow();

        assertEquals(RefundItemStatus.DELIVERED, dbDiamond.status());
        assertEquals(32, dbDiamond.deliveredQuantity());

        assertEquals(RefundItemStatus.PENDING, dbSword.status());
        assertEquals(0, dbSword.deliveredQuantity());

        Refund dbRefund = refundRepository.findById(refundId).orElseThrow();
        assertFalse(dbRefund.isCompleted());
    }

    @Test
    void repeatedDeliveryNoDuplicateItems() throws Exception {
        seedRefundWithItem(10, 0, RefundItemStatus.PENDING);

        refundService.executeRefund(claimId, null, new MockDeliveryAdapter(10));

        RefundItem afterFirst = getFirstItem();
        assertEquals(10, afterFirst.deliveredQuantity());
        assertEquals(RefundItemStatus.DELIVERED, afterFirst.status());

        RefundService.ExecuteRefundResult result2 =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(10));

        assertTrue(result2.isAlreadyCompleted());

        RefundItem afterSecond = getFirstItem();
        assertEquals(10, afterSecond.deliveredQuantity(),
                "Delivered quantity must not increase after second execution");
        assertEquals(RefundItemStatus.DELIVERED, afterSecond.status());
    }

    @Test
    void partialDeliveryThenFullDeliveryCompletesCorrectly() throws Exception {
        seedRefundWithItem(64, 0, RefundItemStatus.PENDING);

        refundService.executeRefund(claimId, null, new MockDeliveryAdapter(20));

        RefundItem afterPartial = getFirstItem();
        assertEquals(20, afterPartial.deliveredQuantity());
        assertEquals(RefundItemStatus.PARTIALLY_DELIVERED, afterPartial.status());

        RefundService.ExecuteRefundResult result2 =
                refundService.executeRefund(claimId, null, new MockDeliveryAdapter(44));

        assertTrue(result2.isSuccess());
        RefundItem afterFull = getFirstItem();
        assertEquals(64, afterFull.deliveredQuantity());
        assertEquals(0, afterFull.remainingQuantity());
        assertEquals(RefundItemStatus.DELIVERED, afterFull.status());

        Refund refund = refundRepository.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.COMPLETED, refund.status());
    }
}
