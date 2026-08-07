package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAction;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItemStatus;
import io.github.skyblueheat.echoclaims.domain.refund.RefundStatus;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision;
import io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundAuditRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundItemRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that orchestrates refund operations with atomic persistence,
 * optimistic concurrency, idempotency guarantees, and audit trail.
 *
 * <p>All methods block on disk I/O and must be called from the query executor,
 * never from the server main thread.</p>
 *
 * <h2>Idempotency Model</h2>
 *
 * <p>Idempotency is enforced at multiple layers:</p>
 * <ul>
 *   <li><b>Database constraint:</b> UNIQUE(review_id) on the refunds table
 *       prevents creating a duplicate refund for the same finalized review.</li>
 *   <li><b>Optimistic concurrency:</b> Every state transition checks the
 *       expected version. Concurrent execution attempts fail with a
 *       concurrency conflict result.</li>
 *   <li><b>Item-level delivery state:</b> Each refund item tracks
 *       {@code deliveredQuantity}. Re-executing delivery only delivers
 *       the remaining quantity.</li>
 *   <li><b>Terminal state check:</b> A COMPLETED refund cannot be
 *       transitioned again. Re-executing delivery on a completed refund
 *       returns a "already completed" result.</li>
 * </ul>
 *
 * <h2>Crash/Recovery Limitations</h2>
 *
 * <p>Paper inventory mutation and SQLite commit cannot be made one atomic
 * transaction. If the server crashes after items have been inserted into the
 * player's inventory but before the delivery state is committed to SQLite,
 * the player will have the items but the refund will still show as
 * undelivered. On the next execution attempt, the item will be re-delivered,
 * resulting in duplication.</p>
 *
 * <p>To minimize this window, the service:</p>
 * <ol>
 *   <li>Updates item delivery state in SQLite immediately after each
 *       item is delivered, not at the end of the batch.</li>
 *   <li>Transitions the refund to DELIVERING before any delivery begins,
 *       so a crash during delivery leaves the refund in a recoverable
 *       state.</li>
 * </ol>
 *
 * <p>The residual crash window is: time between ItemStack insertion and
 * SQLite commit for that item. This is typically milliseconds. Full
 * exactly-once delivery across process crash boundaries is not possible
 * without a distributed transaction coordinator, which is out of scope.</p>
 */
public final class RefundService {

    private final RefundRepository refundRepository;
    private final RefundItemRepository itemRepository;
    private final RefundAuditRepository auditRepository;
    private final RefundStore refundStore;
    private final ClaimReviewRepository reviewRepository;
    private final ClaimReviewItemDecisionRepository decisionRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final RefundServiceConfig config;

    public RefundService(
            RefundRepository refundRepository,
            RefundItemRepository itemRepository,
            RefundAuditRepository auditRepository,
            RefundStore refundStore,
            ClaimReviewRepository reviewRepository,
            ClaimReviewItemDecisionRepository decisionRepository,
            InventorySnapshotRepository snapshotRepository,
            RefundServiceConfig config
    ) {
        this.refundRepository = Objects.requireNonNull(refundRepository, "refundRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.refundStore = Objects.requireNonNull(refundStore, "refundStore");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
        this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository");
        this.config = Objects.requireNonNull(config, "config");
    }

    // ─── Create Refund ───

    /**
     * Creates a refund from a finalized review with APPROVED or PARTIALLY_APPROVED outcome.
     *
     * <p>Refund items are derived from the final item-level review decisions.
     * Only items with outcome APPROVED or PARTIALLY_APPROVED become refundable.
     * REJECTED items are never refundable.</p>
     *
     * <p>This method is idempotent at the database level: if a refund already
     * exists for the given review, it returns an "already exists" result
     * rather than creating a duplicate.</p>
     *
     * @param claim    the claim associated with the review
     * @param review   the finalized review
     * @param decisions the item-level decisions from the review
     * @return the creation result
     */
    public CreateRefundResult createRefund(
            Claim claim,
            ClaimReview review,
            List<ClaimReviewItemDecision> decisions
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(decisions, "decisions");

        if (!review.isFinalized()) {
            return CreateRefundResult.rejected("Review must be finalized to create a refund");
        }

        if (review.finalOutcome() == ReviewOutcome.REJECTED) {
            return CreateRefundResult.rejected("Cannot create refund for a REJECTED review");
        }

        if (review.finalOutcome() != ReviewOutcome.APPROVED
                && review.finalOutcome() != ReviewOutcome.PARTIALLY_APPROVED) {
            return CreateRefundResult.rejected("Review outcome must be APPROVED or PARTIALLY_APPROVED");
        }

        if (!review.claimId().equals(claim.id())) {
            return CreateRefundResult.rejected("Review does not belong to this claim");
        }

        try {
            Optional<Refund> existing = refundRepository.findByReviewId(review.id());
            if (existing.isPresent()) {
                return CreateRefundResult.alreadyExists(existing.get());
            }

            List<RefundItem> refundItems = buildRefundItems(claim, review, decisions);
            if (refundItems.isEmpty()) {
                return CreateRefundResult.rejected(
                        "No refundable items found in review decisions "
                                + "(all items rejected or no approved quantities)");
            }

            long now = System.currentTimeMillis();
            UUID refundId = UUID.randomUUID();

            Refund refund = new Refund(
                    refundId, claim.id(), review.id(), claim.playerUuid(),
                    RefundStatus.PENDING, now, 0, 0,
                    java.util.Map.of()
            );

            RefundAuditEntry refundAudit = new RefundAuditEntry(
                    UUID.randomUUID(), refundId, claim.id(), review.id(),
                    claim.playerUuid(), null, RefundAction.REFUND_CREATED,
                    null, 0, "", now, java.util.Map.of()
            );

            ClaimAuditEntry claimAudit = new ClaimAuditEntry(
                    UUID.randomUUID(), claim.id(), ClaimActorType.SYSTEM, null,
                    ClaimAction.REFUND_CREATED, "Refund created from finalized review",
                    now, claim.version(), java.util.Map.of(
                            "refundId", refundId.toString(),
                            "reviewId", review.id().toString()
                    )
            );

            refundStore.createRefund(refund, refundItems, refundAudit, claimAudit);

            return CreateRefundResult.success(refund, refundItems);

        } catch (SQLException e) {
            return CreateRefundResult.error(e.getMessage());
        }
    }

    private List<RefundItem> buildRefundItems(
            Claim claim, ClaimReview review, List<ClaimReviewItemDecision> decisions
    ) throws SQLException {
        List<RefundItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (ClaimReviewItemDecision decision : decisions) {
            if (decision.outcome() == ReviewItemOutcome.REJECTED) {
                continue;
            }

            if (decision.approvedQuantity() <= 0) {
                continue;
            }

            if (!decision.reviewId().equals(review.id())) {
                continue;
            }

            String slot = extractSlotFromReference(decision.evidenceItemReference());
            String serializedData = resolveSerializedData(decision.sourceSnapshotId(), slot);
            String materialKey = resolveMaterialKey(decision.sourceSnapshotId(), slot, decision);

            UUID itemId = UUID.randomUUID();
            items.add(new RefundItem(
                    itemId, review.id(), claim.id(), review.id(),
                    claim.playerUuid(),
                    decision.evidenceItemReference(),
                    decision.sourceSnapshotId(),
                    materialKey,
                    decision.approvedQuantity(),
                    0,
                    serializedData,
                    RefundItemStatus.PENDING,
                    "",
                    now, now, 0
            ));
        }

        return List.copyOf(items);
    }

    private String resolveSerializedData(UUID snapshotId, String slot) throws SQLException {
        if (slot.isEmpty()) {
            return "";
        }
        Optional<InventorySnapshot> snapshotOpt = snapshotRepository.findById(snapshotId);
        if (snapshotOpt.isEmpty()) {
            return "";
        }
        for (SnapshotItem item : snapshotOpt.get().allItems()) {
            if (item.slot().equals(slot)) {
                return item.serializedData();
            }
        }
        return "";
    }

    private String resolveMaterialKey(UUID snapshotId, String slot, ClaimReviewItemDecision decision) throws SQLException {
        if (!slot.isEmpty()) {
            Optional<InventorySnapshot> snapshotOpt = snapshotRepository.findById(snapshotId);
            if (snapshotOpt.isPresent()) {
                for (SnapshotItem item : snapshotOpt.get().allItems()) {
                    if (item.slot().equals(slot)) {
                        return item.materialKey();
                    }
                }
            }
        }
        return extractMaterialKey(decision);
    }

    private static String extractSlotFromReference(String reference) {
        int colonIdx = reference.lastIndexOf(':');
        if (colonIdx >= 0 && colonIdx < reference.length() - 1) {
            return reference.substring(colonIdx + 1);
        }
        return "";
    }

    private static String extractMaterialKey(ClaimReviewItemDecision decision) {
        String ref = decision.evidenceItemReference();
        int colonIdx = ref.indexOf(':');
        if (colonIdx >= 0 && colonIdx < ref.length() - 1) {
            return ref.substring(colonIdx + 1);
        }
        return ref;
    }

    // ─── Execute Refund ───

    /**
     * Executes refund delivery for a claim. This is the main delivery entry point.
     *
     * <p>The method:</p>
     * <ol>
     *   <li>Looks up the refund by claim ID.</li>
     *   <li>Checks that the refund is in a deliverable state.</li>
     *   <li>Transitions the refund to DELIVERING (optimistic concurrency).</li>
     *   <li>For each pending refund item, calls the delivery adapter to
     *       insert items into the player's inventory.</li>
     *   <li>Updates each item's delivery state in the database immediately
     *       after delivery.</li>
     *   <li>If all items are fully delivered, transitions the refund to COMPLETED.</li>
     *   <li>If some items remain pending (inventory full), transitions back to READY.</li>
     * </ol>
     *
     * <p>This method is idempotent: re-executing on a completed refund returns
     * "already completed". Re-executing on a partial refund only delivers
     * remaining quantities.</p>
     *
     * @param claimId    the claim UUID
     * @param actorUuid  the staff member executing the refund (may be null for system)
     * @param adapter    the delivery adapter that performs actual inventory insertion
     * @return the execution result
     */
    public ExecuteRefundResult executeRefund(
            UUID claimId,
            UUID actorUuid,
            RefundDeliveryAdapter adapter
    ) {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(adapter, "adapter");

        try {
            Optional<Refund> refundOpt = refundRepository.findByClaimId(claimId);
            if (refundOpt.isEmpty()) {
                return ExecuteRefundResult.rejected("No refund found for this claim");
            }

            Refund refund = refundOpt.get();

            if (refund.isCompleted()) {
                return ExecuteRefundResult.alreadyCompleted(refund);
            }

            if (refund.isFailed()) {
                return ExecuteRefundResult.rejected(
                        "Refund is in FAILED state — use retry to attempt delivery again");
            }

            if (!refund.isDeliverable()) {
                return ExecuteRefundResult.rejected(
                        "Refund is in state " + refund.status() + " and cannot be delivered");
            }

            // Transition to DELIVERING
            long now = System.currentTimeMillis();
            RefundAuditEntry startAudit = new RefundAuditEntry(
                    UUID.randomUUID(), refund.id(), refund.claimId(), refund.reviewId(),
                    refund.playerUuid(), actorUuid, RefundAction.REFUND_EXECUTION_STARTED,
                    null, 0, "", now, java.util.Map.of()
            );
            ClaimAuditEntry startClaimAudit = new ClaimAuditEntry(
                    UUID.randomUUID(), refund.claimId(), ClaimActorType.STAFF, actorUuid,
                    ClaimAction.REFUND_EXECUTION_STARTED, "Refund execution started",
                    now, refund.version(), java.util.Map.of(
                            "refundId", refund.id().toString()
                    )
            );

            boolean started = refundStore.startDelivery(
                    refund.id(), refund.version(), startAudit, startClaimAudit);
            if (!started) {
                return ExecuteRefundResult.concurrencyConflict();
            }

            // Re-read refund to get updated version
            refund = refundRepository.findById(refund.id()).orElseThrow();
            int refundVersion = refund.version();

            // Get pending items
            List<RefundItem> pendingItems = itemRepository.findPendingByRefundId(refund.id());
            if (pendingItems.isEmpty()) {
                // All items already delivered — complete the refund
                return completeRefund(refund, refundVersion, actorUuid);
            }

            // Limit items per execution
            int itemsToProcess = Math.min(pendingItems.size(), config.maxItemsPerExecution());
            List<RefundItem> itemsToDeliver = pendingItems.subList(0, itemsToProcess);

            List<RefundItem> deliveredItems = new ArrayList<>();
            List<RefundItem> partiallyDeliveredItems = new ArrayList<>();
            List<RefundItem> failedItems = new ArrayList<>();
            int totalDeliveredQuantity = 0;

            for (RefundItem item : itemsToDeliver) {
                RefundDeliveryAdapter.ItemDeliveryResult result = adapter.deliverItem(item);

                long itemNow = System.currentTimeMillis();
                int newDeliveredQty = item.deliveredQuantity() + result.quantityDelivered();
                RefundItemStatus newStatus;
                String failureReason = "";

                if (!result.failure() && result.quantityDelivered() == 0) {
                    // Nothing was delivered and no failure — inventory full or similar.
                    // Leave the item in its current state; no DB update needed.
                    continue;
                }

                if (result.failure()) {
                    newStatus = RefundItemStatus.FAILED;
                    failureReason = result.failureReason();
                    failedItems.add(item);
                } else if (newDeliveredQty >= item.refundableQuantity()) {
                    newStatus = RefundItemStatus.DELIVERED;
                    deliveredItems.add(item);
                } else {
                    newStatus = RefundItemStatus.PARTIALLY_DELIVERED;
                    partiallyDeliveredItems.add(item);
                }

                RefundAuditEntry itemAudit = new RefundAuditEntry(
                        UUID.randomUUID(), refund.id(), refund.claimId(), refund.reviewId(),
                        refund.playerUuid(), actorUuid,
                        newStatus == RefundItemStatus.DELIVERED
                                ? RefundAction.REFUND_ITEM_DELIVERED
                                : newStatus == RefundItemStatus.PARTIALLY_DELIVERED
                                ? RefundAction.REFUND_PARTIALLY_DELIVERED
                                : RefundAction.REFUND_FAILED,
                        item.id(), result.quantityDelivered(),
                        failureReason, itemNow, java.util.Map.of()
                );

                boolean itemUpdated = refundStore.updateItemDelivery(
                        item.id(), newDeliveredQty, newStatus, failureReason,
                        itemNow, item.version(), itemAudit);
                if (!itemUpdated) {
                    // Version conflict on this item — skip it, it may have been
                    // updated by a concurrent execution
                    continue;
                }

                totalDeliveredQuantity += result.quantityDelivered();
            }

            // Re-read refund to get current version
            refund = refundRepository.findById(refund.id()).orElseThrow();
            refundVersion = refund.version();

            // Check if all items are now delivered
            List<RefundItem> stillPending = itemRepository.findPendingByRefundId(refund.id());

            if (!failedItems.isEmpty() && stillPending.isEmpty()) {
                // All items processed but some failed
                return failRefund(refund, refundVersion, actorUuid,
                        "One or more items failed to deliver");
            }

            if (stillPending.isEmpty()) {
                // All items delivered
                return completeRefund(refund, refundVersion, actorUuid);
            }

            // Some items remain pending (partial delivery or inventory full)
            // Transition back to READY
            return partialDelivery(refund, refundVersion, actorUuid,
                    totalDeliveredQuantity, deliveredItems.size(),
                    partiallyDeliveredItems.size(), stillPending.size());

        } catch (SQLException e) {
            return ExecuteRefundResult.error(e.getMessage());
        }
    }

    private ExecuteRefundResult completeRefund(Refund refund, int refundVersion, UUID actorUuid) throws SQLException {
        long now = System.currentTimeMillis();
        RefundAuditEntry completeAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), refund.claimId(), refund.reviewId(),
                refund.playerUuid(), actorUuid, RefundAction.REFUND_COMPLETED,
                null, 0, "", now, java.util.Map.of()
        );
        ClaimAuditEntry completeClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), refund.claimId(), ClaimActorType.STAFF, actorUuid,
                ClaimAction.REFUND_COMPLETED, "Refund completed — all items delivered",
                now, refundVersion, java.util.Map.of(
                        "refundId", refund.id().toString()
                )
        );

        boolean completed = refundStore.completeRefund(
                refund.id(), now, refundVersion, completeAudit, completeClaimAudit);
        if (!completed) {
            return ExecuteRefundResult.concurrencyConflict();
        }

        Refund updated = refundRepository.findById(refund.id()).orElseThrow();
        return ExecuteRefundResult.completed(updated);
    }

    private ExecuteRefundResult failRefund(Refund refund, int refundVersion, UUID actorUuid, String reason) throws SQLException {
        long now = System.currentTimeMillis();
        RefundAuditEntry failAudit = new RefundAuditEntry(
                UUID.randomUUID(), refund.id(), refund.claimId(), refund.reviewId(),
                refund.playerUuid(), actorUuid, RefundAction.REFUND_FAILED,
                null, 0, reason, now, java.util.Map.of()
        );
        ClaimAuditEntry failClaimAudit = new ClaimAuditEntry(
                UUID.randomUUID(), refund.claimId(), ClaimActorType.STAFF, actorUuid,
                ClaimAction.REFUND_FAILED, "Refund failed: " + reason,
                now, refundVersion, java.util.Map.of(
                        "refundId", refund.id().toString()
                )
        );

        boolean failed = refundStore.failRefund(
                refund.id(), reason, refundVersion, failAudit, failClaimAudit);
        if (!failed) {
            return ExecuteRefundResult.concurrencyConflict();
        }

        Refund updated = refundRepository.findById(refund.id()).orElseThrow();
        return ExecuteRefundResult.failed(updated, reason);
    }

    private ExecuteRefundResult partialDelivery(
            Refund refund, int refundVersion, UUID actorUuid,
            int totalDelivered, int fullyDeliveredCount,
            int partiallyDeliveredCount, int remainingCount
    ) throws SQLException {
        // Refund stays in DELIVERING — we need to transition back to READY
        // But we need to re-read the refund since item updates may have
        // changed the version through the store
        Refund current = refundRepository.findById(refund.id()).orElseThrow();

        // Use repository.updateStatus to transition back to READY
        boolean updated = refundRepository.updateStatus(
                current.id(), RefundStatus.READY, 0, current.version());
        if (!updated) {
            // Version conflict — another execution may have already completed
            Refund refreshed = refundRepository.findById(refund.id()).orElseThrow();
            if (refreshed.isCompleted()) {
                return ExecuteRefundResult.completed(refreshed);
            }
            return ExecuteRefundResult.concurrencyConflict();
        }

        Refund updatedRefund = refundRepository.findById(refund.id()).orElseThrow();
        return ExecuteRefundResult.partialDelivery(
                updatedRefund, totalDelivered, fullyDeliveredCount,
                partiallyDeliveredCount, remainingCount);
    }

    // ─── Find Pending Refunds ───

    /**
     * Finds all pending (PENDING or READY) refunds for a player.
     */
    public List<Refund> findPendingRefundsForPlayer(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        try {
            List<Refund> pending = refundRepository.findByPlayerUuidAndStatus(
                    playerUuid, RefundStatus.PENDING, 50);
            List<Refund> ready = refundRepository.findByPlayerUuidAndStatus(
                    playerUuid, RefundStatus.READY, 50);
            List<Refund> all = new ArrayList<>(pending);
            all.addAll(ready);
            return List.copyOf(all);
        } catch (SQLException e) {
            return List.of();
        }
    }

    // ─── Retry Failed Refund ───

    /**
     * Retries a failed refund by transitioning it back to READY.
     *
     * @param claimId   the claim UUID
     * @param actorUuid the staff member retrying the refund
     * @return the retry result
     */
    public RetryRefundResult retryRefund(UUID claimId, UUID actorUuid) {
        Objects.requireNonNull(claimId, "claimId");
        if (!config.retryFailedRefunds()) {
            return RetryRefundResult.rejected("Retry of failed refunds is disabled");
        }

        try {
            Optional<Refund> refundOpt = refundRepository.findByClaimId(claimId);
            if (refundOpt.isEmpty()) {
                return RetryRefundResult.rejected("No refund found for this claim");
            }

            Refund refund = refundOpt.get();
            if (!refund.isFailed()) {
                return RetryRefundResult.rejected(
                        "Refund is not in FAILED state (current: " + refund.status() + ")");
            }

            long now = System.currentTimeMillis();
            RefundAuditEntry retryAudit = new RefundAuditEntry(
                    UUID.randomUUID(), refund.id(), refund.claimId(), refund.reviewId(),
                    refund.playerUuid(), actorUuid, RefundAction.REFUND_RETRIED,
                    null, 0, "", now, java.util.Map.of()
            );
            ClaimAuditEntry retryClaimAudit = new ClaimAuditEntry(
                    UUID.randomUUID(), refund.claimId(), ClaimActorType.STAFF, actorUuid,
                    ClaimAction.REFUND_RETRIED, "Refund retry initiated",
                    now, refund.version(), java.util.Map.of(
                            "refundId", refund.id().toString()
                    )
            );

            boolean retried = refundStore.retryRefund(
                    refund.id(), RefundStatus.READY, refund.version(),
                    retryAudit, retryClaimAudit);
            if (!retried) {
                return RetryRefundResult.concurrencyConflict();
            }

            Refund updated = refundRepository.findById(refund.id()).orElseThrow();
            return RetryRefundResult.success(updated);

        } catch (SQLException e) {
            return RetryRefundResult.error(e.getMessage());
        }
    }

    // ─── Get Refund History ───

    /**
     * Gets the refund and its items for a claim.
     */
    public Optional<RefundDetail> findRefundByClaimId(UUID claimId) {
        Objects.requireNonNull(claimId, "claimId");
        try {
            Optional<Refund> refundOpt = refundRepository.findByClaimId(claimId);
            if (refundOpt.isEmpty()) {
                return Optional.empty();
            }
            Refund refund = refundOpt.get();
            List<RefundItem> items = itemRepository.findByRefundId(refund.id());
            List<RefundAuditEntry> audit = auditRepository.findByRefundId(refund.id());
            return Optional.of(new RefundDetail(refund, items, audit));
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets refund audit history for a claim.
     */
    public List<RefundAuditEntry> getRefundHistory(UUID claimId) {
        Objects.requireNonNull(claimId, "claimId");
        try {
            return auditRepository.findByClaimId(claimId);
        } catch (SQLException e) {
            return List.of();
        }
    }

    // ─── Result Types ───

    public static final class CreateRefundResult {
        private final boolean success;
        private final boolean alreadyExists;
        private final String rejectionReason;
        private final Refund refund;
        private final List<RefundItem> items;

        private CreateRefundResult(boolean success, boolean alreadyExists,
                                   String rejectionReason, Refund refund, List<RefundItem> items) {
            this.success = success; this.alreadyExists = alreadyExists;
            this.rejectionReason = rejectionReason; this.refund = refund; this.items = items;
        }
        static CreateRefundResult success(Refund refund, List<RefundItem> items) {
            return new CreateRefundResult(true, false, null, refund, items);
        }
        static CreateRefundResult alreadyExists(Refund refund) {
            return new CreateRefundResult(false, true, "Refund already exists for this review", refund, null);
        }
        static CreateRefundResult rejected(String reason) {
            return new CreateRefundResult(false, false, reason, null, null);
        }
        static CreateRefundResult error(String msg) {
            return new CreateRefundResult(false, false, "Database error: " + msg, null, null);
        }
        public boolean isSuccess() { return success; }
        public boolean isAlreadyExists() { return alreadyExists; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
        public Optional<Refund> refund() { return Optional.ofNullable(refund); }
        public Optional<List<RefundItem>> items() { return Optional.ofNullable(items); }
    }

    public static final class ExecuteRefundResult {
        private final boolean success;
        private final boolean alreadyCompleted;
        private final boolean concurrencyConflict;
        private final boolean partialDelivery;
        private final boolean failed;
        private final String rejectionReason;
        private final Refund refund;
        private final int totalDelivered;
        private final int fullyDeliveredCount;
        private final int partiallyDeliveredCount;
        private final int remainingCount;

        private ExecuteRefundResult(boolean success, boolean alreadyCompleted,
                                    boolean concurrencyConflict, boolean partialDelivery,
                                    boolean failed, String rejectionReason, Refund refund,
                                    int totalDelivered, int fullyDeliveredCount,
                                    int partiallyDeliveredCount, int remainingCount) {
            this.success = success; this.alreadyCompleted = alreadyCompleted;
            this.concurrencyConflict = concurrencyConflict; this.partialDelivery = partialDelivery;
            this.failed = failed; this.rejectionReason = rejectionReason; this.refund = refund;
            this.totalDelivered = totalDelivered; this.fullyDeliveredCount = fullyDeliveredCount;
            this.partiallyDeliveredCount = partiallyDeliveredCount; this.remainingCount = remainingCount;
        }
        static ExecuteRefundResult completed(Refund refund) {
            return new ExecuteRefundResult(true, false, false, false, false, null, refund, 0, 0, 0, 0);
        }
        static ExecuteRefundResult alreadyCompleted(Refund refund) {
            return new ExecuteRefundResult(false, true, false, false, false, "Refund already completed", refund, 0, 0, 0, 0);
        }
        static ExecuteRefundResult partialDelivery(Refund refund, int total, int full, int partial, int remaining) {
            return new ExecuteRefundResult(true, false, false, true, false, null, refund, total, full, partial, remaining);
        }
        static ExecuteRefundResult failed(Refund refund, String reason) {
            return new ExecuteRefundResult(false, false, false, false, true, reason, refund, 0, 0, 0, 0);
        }
        static ExecuteRefundResult rejected(String reason) {
            return new ExecuteRefundResult(false, false, false, false, false, reason, null, 0, 0, 0, 0);
        }
        static ExecuteRefundResult concurrencyConflict() {
            return new ExecuteRefundResult(false, false, true, false, false, "Concurrency conflict", null, 0, 0, 0, 0);
        }
        static ExecuteRefundResult error(String msg) {
            return new ExecuteRefundResult(false, false, false, false, false, "Database error: " + msg, null, 0, 0, 0, 0);
        }
        public boolean isSuccess() { return success; }
        public boolean isAlreadyCompleted() { return alreadyCompleted; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public boolean isPartialDelivery() { return partialDelivery; }
        public boolean isFailed() { return failed; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
        public Optional<Refund> refund() { return Optional.ofNullable(refund); }
        public int totalDelivered() { return totalDelivered; }
        public int fullyDeliveredCount() { return fullyDeliveredCount; }
        public int partiallyDeliveredCount() { return partiallyDeliveredCount; }
        public int remainingCount() { return remainingCount; }
    }

    public static final class RetryRefundResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final String rejectionReason;
        private final Refund refund;

        private RetryRefundResult(boolean success, boolean concurrencyConflict,
                                  String rejectionReason, Refund refund) {
            this.success = success; this.concurrencyConflict = concurrencyConflict;
            this.rejectionReason = rejectionReason; this.refund = refund;
        }
        static RetryRefundResult success(Refund refund) {
            return new RetryRefundResult(true, false, null, refund);
        }
        static RetryRefundResult rejected(String reason) {
            return new RetryRefundResult(false, false, reason, null);
        }
        static RetryRefundResult concurrencyConflict() {
            return new RetryRefundResult(false, true, "Concurrency conflict", null);
        }
        static RetryRefundResult error(String msg) {
            return new RetryRefundResult(false, false, "Database error: " + msg, null);
        }
        public boolean isSuccess() { return success; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
        public Optional<Refund> refund() { return Optional.ofNullable(refund); }
    }

    public record RefundDetail(Refund refund, List<RefundItem> items, List<RefundAuditEntry> audit) {
        public RefundDetail {
            Objects.requireNonNull(refund, "refund");
            items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
            audit = List.copyOf(Objects.requireNonNullElse(audit, List.of()));
        }
    }
}
