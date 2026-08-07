package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.application.RefundDeliveryAdapter;
import io.github.skyblueheat.echoclaims.application.RefundService;
import io.github.skyblueheat.echoclaims.application.RefundServiceConfig;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.skyblueheat.echoclaims.persistence.ClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundAuditRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundStoreConcurrencyTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private IncidentRepository incidentRepository;
    private InventorySnapshotRepository snapshotRepository;
    private RefundRepository refundRepository;
    private RefundItemRepository itemRepository;
    private RefundStore refundStore;

    private UUID playerUuid;
    private UUID claimId;
    private UUID reviewId;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("refund_concurrency.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
        refundRepository = new SqliteRefundRepository(databaseManager);
        itemRepository = new SqliteRefundItemRepository(databaseManager);
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

    private Refund seedRefund() throws Exception {
        UUID refundId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.PENDING, now, 0, 0, Map.of()
        );
        RefundItem item = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond_sword", 5, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );
        RefundAuditEntry refundAudit = new RefundAuditEntry(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_CREATED, null, 0, "",
                now, Map.of()
        );
        ClaimAuditEntry claimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_CREATED, "refund",
                now, 0, Map.of()
        );
        refundStore.createRefund(refund, List.of(item), refundAudit, claimAudit);
        return refund;
    }

    @Test
    void concurrentStartDeliveryExactlyOneSucceeds() throws Exception {
        Refund refund = seedRefund();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    RefundAuditEntry audit = new RefundAuditEntry(
                            UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                            null, RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                            System.currentTimeMillis(), Map.of()
                    );
                    ClaimAuditEntry claimAudit = new ClaimAuditEntry(
                            UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                            null, ClaimAction.REFUND_EXECUTION_STARTED, "delivery",
                            System.currentTimeMillis(), 0, Map.of()
                    );
                    boolean ok = refundStore.startDelivery(refund.id(), 0, audit, claimAudit);
                    if (ok) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one startDelivery should succeed");
        assertEquals(threadCount - 1, failures.get(), "All others should fail");
    }

    @Test
    void concurrentCompleteRefundExactlyOneSucceeds() throws Exception {
        Refund refund = seedRefund();

        RefundAuditEntry startAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry startClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_EXECUTION_STARTED, "delivery",
                System.currentTimeMillis(), 0, Map.of()
        );
        assertTrue(refundStore.startDelivery(refund.id(), 0, startAudit, startClaimAudit));

        Optional<Refund> delivering = refundRepository.findById(refund.id());
        assertTrue(delivering.isPresent());
        int version = delivering.get().version();

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    RefundAuditEntry audit = new RefundAuditEntry(
                            UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                            null, RefundAction.REFUND_COMPLETED, null, 0, "",
                            System.currentTimeMillis(), Map.of()
                    );
                    ClaimAuditEntry claimAudit = new ClaimAuditEntry(
                            UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                            null, ClaimAction.REFUND_COMPLETED, "completed",
                            System.currentTimeMillis(), 0, Map.of()
                    );
                    boolean ok = refundStore.completeRefund(
                            refund.id(), System.currentTimeMillis(), version, audit, claimAudit);
                    if (ok) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one completeRefund should succeed");
        assertEquals(threadCount - 1, failures.get(), "All others should fail");
    }

    @Test
    void concurrentItemDeliveryUpdatesExactlyOneSucceeds() throws Exception {
        Refund refund = seedRefund();

        RefundAuditEntry startAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry startClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_EXECUTION_STARTED, "delivery",
                System.currentTimeMillis(), 0, Map.of()
        );
        assertTrue(refundStore.startDelivery(refund.id(), 0, startAudit, startClaimAudit));

        List<RefundItem> items = itemRepository.findByRefundId(refund.id());
        assertEquals(1, items.size());
        UUID itemId = items.get(0).id();

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    RefundAuditEntry audit = new RefundAuditEntry(
                            UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                            null, RefundAction.REFUND_ITEM_DELIVERED, itemId, 5, "",
                            System.currentTimeMillis(), Map.of()
                    );
                    boolean ok = refundStore.updateItemDelivery(
                            itemId, 5, RefundItemStatus.DELIVERED, "",
                            System.currentTimeMillis(), 0, audit);
                    if (ok) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one item delivery should succeed");
        assertEquals(threadCount - 1, failures.get(), "All others should fail");
    }

    // ─── 4. Concurrent refund creation for the same review ───

    @Test
    void concurrentCreateRefundForSameReviewExactlyOneSucceeds() throws Exception {
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    Refund refund = new Refund(
                            refundId, claimId, reviewId, playerUuid,
                            RefundStatus.PENDING, now, 0, 0, Map.of()
                    );
                    RefundItem item = new RefundItem(
                            UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                            "snap:0", UUID.randomUUID(), "diamond", 5, 0,
                            "base64data", RefundItemStatus.PENDING, "", now, now, 0
                    );
                    RefundAuditEntry refundAudit = new RefundAuditEntry(
                            UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                            null, RefundAction.REFUND_CREATED, null, 0, "",
                            now, Map.of()
                    );
                    ClaimAuditEntry claimAudit = new ClaimAuditEntry(
                            UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                            null, ClaimAction.REFUND_CREATED, "refund",
                            now, 0, Map.of()
                    );
                    refundStore.createRefund(refund, List.of(item), refundAudit, claimAudit);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one createRefund should succeed");
        assertEquals(threadCount - 1, failures.get());

        Optional<Refund> found = refundRepository.findById(refundId);
        assertTrue(found.isPresent());
        List<RefundItem> items = itemRepository.findByRefundId(refundId);
        assertEquals(1, items.size(), "Exactly one item row should exist");
    }

    // ─── 5. Repeated execute calls — no duplication ───

    @Test
    void repeatedExecuteCallsDoNotDuplicateDelivery() throws Exception {
        Refund refund = seedRefund();

        RefundAuditEntry startAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry startClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_EXECUTION_STARTED, "delivery",
                System.currentTimeMillis(), 0, Map.of()
        );
        assertTrue(refundStore.startDelivery(refund.id(), 0, startAudit, startClaimAudit));

        List<RefundItem> items = itemRepository.findByRefundId(refund.id());
        UUID itemId = items.get(0).id();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    RefundAuditEntry audit = new RefundAuditEntry(
                            UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                            null, RefundAction.REFUND_ITEM_DELIVERED, itemId, 5, "",
                            System.currentTimeMillis(), Map.of()
                    );
                    boolean ok = refundStore.updateItemDelivery(
                            itemId, 5, RefundItemStatus.DELIVERED, "",
                            System.currentTimeMillis(), 0, audit);
                    if (ok) successes.incrementAndGet();
                } catch (Exception e) {
                    // expected for losers
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Only one update should succeed");

        RefundItem item = itemRepository.findById(itemId).orElseThrow();
        assertEquals(5, item.deliveredQuantity(), "Delivered quantity must not be duplicated");
        assertTrue(item.deliveredQuantity() <= item.refundableQuantity(),
                "deliveredQuantity must never exceed refundableQuantity");
    }

    // ─── 6. COMPLETED refund executed again — no effect ───

    @Test
    void completedRefundReExecutionHasNoEffect() throws Exception {
        Refund refund = seedRefund();

        RefundAuditEntry startAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry startClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_EXECUTION_STARTED, "delivery",
                System.currentTimeMillis(), 0, Map.of()
        );
        assertTrue(refundStore.startDelivery(refund.id(), 0, startAudit, startClaimAudit));

        Optional<Refund> delivering = refundRepository.findById(refund.id());
        int version = delivering.get().version();

        List<RefundItem> items = itemRepository.findByRefundId(refund.id());
        UUID itemId = items.get(0).id();

        RefundAuditEntry itemDeliveryAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_ITEM_DELIVERED, itemId, 5, "",
                System.currentTimeMillis(), Map.of()
        );
        assertTrue(refundStore.updateItemDelivery(
                itemId, 5, RefundItemStatus.DELIVERED, "",
                System.currentTimeMillis(), 0, itemDeliveryAudit));

        RefundAuditEntry completeAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_COMPLETED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry completeClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_COMPLETED, "done",
                System.currentTimeMillis(), 0, Map.of()
        );

        Optional<Refund> beforeComplete = refundRepository.findById(refund.id());
        assertTrue(refundStore.completeRefund(refund.id(), System.currentTimeMillis(),
                beforeComplete.get().version(), completeAudit, completeClaimAudit));

        Optional<Refund> completed = refundRepository.findById(refund.id());
        assertEquals(RefundStatus.COMPLETED, completed.get().status());

        RefundAuditEntry secondCompleteAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_COMPLETED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry secondCompleteClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_COMPLETED, "done2",
                System.currentTimeMillis(), 0, Map.of()
        );
        boolean secondAttempt = refundStore.completeRefund(refund.id(),
                System.currentTimeMillis(), completed.get().version(),
                secondCompleteAudit, secondCompleteClaimAudit);
        assertFalse(secondAttempt, "Second completion on COMPLETED refund must fail");

        RefundItem item = itemRepository.findById(itemId).orElseThrow();
        assertEquals(5, item.deliveredQuantity(), "Delivered quantity must not change");
        assertEquals(RefundItemStatus.DELIVERED, item.status());
    }

    // ─── 7. Player claim and staff execute simultaneously ───

    @Test
    void playerClaimAndStaffExecuteSimultaneouslyOnlyOneSucceeds() throws Exception {
        Refund refund = seedRefund();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    RefundAuditEntry audit = new RefundAuditEntry(
                            UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                            idx == 0 ? null : null,
                            RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                            System.currentTimeMillis(), Map.of()
                    );
                    ClaimAuditEntry claimAudit = new ClaimAuditEntry(
                            UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                            null, ClaimAction.REFUND_EXECUTION_STARTED,
                            idx == 0 ? "player-claim" : "staff-execute",
                            System.currentTimeMillis(), 0, Map.of()
                    );
                    boolean ok = refundStore.startDelivery(refund.id(), 0, audit, claimAudit);
                    if (ok) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Only one of player/staff should win the delivery lock");
        assertEquals(1, failures.get());

        Refund dbRefund = refundRepository.findById(refund.id()).orElseThrow();
        assertEquals(RefundStatus.DELIVERING, dbRefund.status());
    }

    // ─── 8. Partial delivery followed by concurrent retry/claim ───

    @Test
    void partialDeliveryFollowedByConcurrentRetryOnlyOneSucceeds() throws Exception {
        Refund refund = seedRefund();

        RefundAuditEntry startAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry startClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_EXECUTION_STARTED, "delivery",
                System.currentTimeMillis(), 0, Map.of()
        );
        assertTrue(refundStore.startDelivery(refund.id(), 0, startAudit, startClaimAudit));

        List<RefundItem> items = itemRepository.findByRefundId(refund.id());
        UUID itemId = items.get(0).id();

        RefundAuditEntry partialAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_PARTIALLY_DELIVERED, itemId, 2, "",
                System.currentTimeMillis(), Map.of()
        );
        assertTrue(refundStore.updateItemDelivery(
                itemId, 2, RefundItemStatus.PARTIALLY_DELIVERED, "",
                System.currentTimeMillis(), 0, partialAudit));

        RefundAuditEntry backToReadyAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                null, RefundAction.REFUND_EXECUTION_STARTED, null, 0, "",
                System.currentTimeMillis(), Map.of()
        );
        ClaimAuditEntry backToReadyClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.SYSTEM,
                null, ClaimAction.REFUND_EXECUTION_STARTED, "back-to-ready",
                System.currentTimeMillis(), 0, Map.of()
        );

        Optional<Refund> delivering = refundRepository.findById(refund.id());
        int version = delivering.get().version();

        assertTrue(refundStore.startDelivery(refund.id(), version, backToReadyAudit, backToReadyClaimAudit));

        Optional<Refund> reDelivering = refundRepository.findById(refund.id());
        int newVersion = reDelivering.get().version();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    RefundAuditEntry audit = new RefundAuditEntry(
                            UUID.randomUUID(), refund.id(), claimId, reviewId, playerUuid,
                            null, RefundAction.REFUND_ITEM_DELIVERED, itemId, 5, "",
                            System.currentTimeMillis(), Map.of()
                    );
                    boolean ok = refundStore.updateItemDelivery(
                            itemId, 5, RefundItemStatus.DELIVERED, "",
                            System.currentTimeMillis(), 1, audit);
                    if (ok) successes.incrementAndGet();
                } catch (Exception e) {
                    // expected
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Only one retry should succeed");

        RefundItem item = itemRepository.findById(itemId).orElseThrow();
        assertEquals(5, item.deliveredQuantity(), "Final delivered must be exactly refundable");
        assertTrue(item.deliveredQuantity() <= item.refundableQuantity(),
                "deliveredQuantity must never exceed refundableQuantity");
    }

    // ─── Helper: RefundAuditEntry for item delivery ───
}
