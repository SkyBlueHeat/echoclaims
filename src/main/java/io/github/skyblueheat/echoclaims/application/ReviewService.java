package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision;
import io.github.skyblueheat.echoclaims.domain.review.CommentType;
import io.github.skyblueheat.echoclaims.domain.review.CommentVisibility;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcomePolicy;
import io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;
import io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewCommentRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewStore;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that orchestrates review operations with atomic persistence,
 * optimistic concurrency, and audit trail.
 *
 * <p>All methods block on disk I/O and must be called from the query executor,
 * never from the server main thread.</p>
 */
public final class ReviewService {

    private final ClaimReviewRepository reviewRepository;
    private final ClaimReviewCommentRepository commentRepository;
    private final ClaimReviewItemDecisionRepository decisionRepository;
    private final ClaimReviewStore reviewStore;
    private final ReviewMetrics metrics;
    private final ReviewServiceConfig config;

    public ReviewService(
            ClaimReviewRepository reviewRepository,
            ClaimReviewCommentRepository commentRepository,
            ClaimReviewItemDecisionRepository decisionRepository,
            ClaimReviewStore reviewStore,
            ReviewMetrics metrics,
            ReviewServiceConfig config
    ) {
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
        this.commentRepository = Objects.requireNonNull(commentRepository, "commentRepository");
        this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
        this.reviewStore = Objects.requireNonNull(reviewStore, "reviewStore");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.config = Objects.requireNonNull(config, "config");
    }

    public StartReviewResult startReview(Claim claim, UUID staffUuid) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(staffUuid, "staffUuid");

        if (claim.status() != ClaimStatus.SUBMITTED) {
            return StartReviewResult.rejected("Claim must be SUBMITTED to start a review");
        }

        try {
            Optional<ClaimReview> existing = reviewRepository.findByClaimId(claim.id());
            if (existing.isPresent()) {
                return StartReviewResult.rejected("A review already exists for this claim");
            }

            long now = System.currentTimeMillis();
            UUID reviewId = UUID.randomUUID();
            ClaimReview review = new ClaimReview(
                    reviewId, claim.id(), staffUuid,
                    ReviewState.OPEN, null, "",
                    now, now, 0, 0,
                    java.util.Map.of()
            );

            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), claim.id(),
                    ClaimActorType.STAFF, staffUuid,
                    ClaimAction.REVIEW_STARTED, "",
                    now, claim.version() + 1,
                    java.util.Map.of("reviewId", reviewId.toString())
            );

            boolean success = reviewStore.startReview(
                    review, claim.id(), claim.version(), auditEntry);

            if (!success) {
                metrics.recordReviewConcurrencyConflict();
                return StartReviewResult.concurrencyConflict();
            }

            metrics.recordReviewStarted();
            return StartReviewResult.success(review);

        } catch (SQLException e) {
            return StartReviewResult.error(e.getMessage());
        }
    }

    public TakeoverResult takeoverReview(ClaimReview review, UUID newReviewerUuid, String reason) {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(newReviewerUuid, "newReviewerUuid");

        if (review.isFinalized()) {
            return TakeoverResult.rejected("Cannot take over a finalized review");
        }
        if (review.assignedReviewerUuid().equals(newReviewerUuid)) {
            return TakeoverResult.rejected("You are already the assigned reviewer");
        }
        String reasonBody = reason == null ? "" : reason.strip();
        if (reasonBody.isEmpty()) {
            return TakeoverResult.rejected("Takeover reason cannot be blank");
        }

        try {
            long now = System.currentTimeMillis();
            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), review.claimId(),
                    ClaimActorType.STAFF, newReviewerUuid,
                    ClaimAction.REVIEW_TAKEN_OVER, reasonBody,
                    now, review.version() + 1,
                    java.util.Map.of(
                            "reviewId", review.id().toString(),
                            "previousReviewer", review.assignedReviewerUuid().toString()
                    )
            );

            boolean success = reviewStore.takeoverReview(
                    review.id(), newReviewerUuid, review.version(), auditEntry);

            if (!success) {
                metrics.recordReviewConcurrencyConflict();
                return TakeoverResult.concurrencyConflict();
            }

            metrics.recordReviewTakenOver();
            return TakeoverResult.success();

        } catch (SQLException e) {
            return TakeoverResult.error(e.getMessage());
        }
    }

    public AddNoteResult addInternalNote(ClaimReview review, UUID staffUuid, String noteBody) {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(noteBody, "noteBody");

        if (review.isFinalized()) {
            return AddNoteResult.rejected("Cannot add notes to a finalized review");
        }
        if (config.requireAssignmentForMutations() && !review.assignedReviewerUuid().equals(staffUuid)) {
            return AddNoteResult.rejected("Only the assigned reviewer can add notes to this review");
        }

        String body = noteBody.strip();
        if (body.isEmpty()) {
            return AddNoteResult.rejected("Note body cannot be blank");
        }
        if (body.length() > config.maxInternalNoteLength()) {
            return AddNoteResult.rejected("Note body exceeds maximum length of " + config.maxInternalNoteLength());
        }

        try {
            long now = System.currentTimeMillis();
            ClaimReviewComment comment = new ClaimReviewComment(
                    UUID.randomUUID(), review.id(), review.claimId(),
                    staffUuid, ClaimActorType.STAFF,
                    CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                    body, now, java.util.Map.of()
            );

            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), review.claimId(),
                    ClaimActorType.STAFF, staffUuid,
                    ClaimAction.INTERNAL_NOTE_ADDED, "",
                    now, review.version() + 1,
                    java.util.Map.of("reviewId", review.id().toString())
            );

            reviewStore.addInternalNote(comment, auditEntry);
            metrics.recordInternalNoteAdded();
            return AddNoteResult.success();

        } catch (SQLException e) {
            return AddNoteResult.error(e.getMessage());
        }
    }

    public RequestInfoResult requestInformation(Claim claim, ClaimReview review, UUID staffUuid, String request) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(request, "request");

        if (review.isFinalized()) {
            return RequestInfoResult.rejected("Cannot request information on a finalized review");
        }
        if (claim.status() != ClaimStatus.UNDER_REVIEW) {
            return RequestInfoResult.rejected("Claim must be UNDER_REVIEW to request information");
        }
        if (config.requireAssignmentForMutations() && !review.assignedReviewerUuid().equals(staffUuid)) {
            return RequestInfoResult.rejected("Only the assigned reviewer can request information on this review");
        }

        String body = request.strip();
        if (body.isEmpty()) {
            return RequestInfoResult.rejected("Information request body cannot be blank");
        }
        if (body.length() > config.maxQuestionLength()) {
            return RequestInfoResult.rejected("Question exceeds maximum length of " + config.maxQuestionLength());
        }

        try {
            long now = System.currentTimeMillis();
            ClaimReviewComment comment = new ClaimReviewComment(
                    UUID.randomUUID(), review.id(), review.claimId(),
                    staffUuid, ClaimActorType.STAFF,
                    CommentVisibility.CLAIM_PARTICIPANTS, CommentType.INFORMATION_REQUEST,
                    body, now, java.util.Map.of()
            );

            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), claim.id(),
                    ClaimActorType.STAFF, staffUuid,
                    ClaimAction.INFORMATION_REQUESTED, body,
                    now, claim.version() + 1,
                    java.util.Map.of("reviewId", review.id().toString())
            );

            boolean success = reviewStore.requestInformation(
                    comment, claim.id(), claim.version(), auditEntry);

            if (!success) {
                metrics.recordReviewConcurrencyConflict();
                return RequestInfoResult.concurrencyConflict();
            }

            metrics.recordInformationRequestSent();
            return RequestInfoResult.success();

        } catch (SQLException e) {
            return RequestInfoResult.error(e.getMessage());
        }
    }

    public PlayerResponseResult recordPlayerResponse(Claim claim, ClaimReview review, UUID playerUuid, String response) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(response, "response");

        if (claim.status() != ClaimStatus.WAITING_FOR_PLAYER) {
            return PlayerResponseResult.rejected("Claim must be WAITING_FOR_PLAYER to respond");
        }
        if (!claim.playerUuid().equals(playerUuid)) {
            return PlayerResponseResult.rejected("Only the claim owner can respond");
        }

        String body = response.strip();
        if (body.isEmpty()) {
            return PlayerResponseResult.rejected("Response body cannot be blank");
        }
        if (body.length() > config.maxPlayerResponseLength()) {
            return PlayerResponseResult.rejected("Response exceeds maximum length of " + config.maxPlayerResponseLength());
        }

        try {
            long now = System.currentTimeMillis();
            ClaimReviewComment comment = new ClaimReviewComment(
                    UUID.randomUUID(), review.id(), review.claimId(),
                    playerUuid, ClaimActorType.PLAYER,
                    CommentVisibility.CLAIM_PARTICIPANTS, CommentType.PLAYER_RESPONSE,
                    body, now, java.util.Map.of()
            );

            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), claim.id(),
                    ClaimActorType.PLAYER, playerUuid,
                    ClaimAction.PLAYER_RESPONDED, body,
                    now, claim.version() + 1,
                    java.util.Map.of("reviewId", review.id().toString())
            );

            boolean success = reviewStore.recordPlayerResponse(
                    comment, claim.id(), claim.version(), auditEntry);

            if (!success) {
                metrics.recordReviewConcurrencyConflict();
                return PlayerResponseResult.concurrencyConflict();
            }

            metrics.recordPlayerResponseReceived();
            return PlayerResponseResult.success();

        } catch (SQLException e) {
            return PlayerResponseResult.error(e.getMessage());
        }
    }

    public ItemDecisionResult createItemDecision(
            ClaimReview review, UUID staffUuid,
            String evidenceItemReference, UUID sourceSnapshotId,
            int originalQuantity, int approvedQuantity,
            ReviewItemOutcome outcome, ReviewReasonCode reasonCode,
            String staffNote
    ) {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(evidenceItemReference, "evidenceItemReference");
        Objects.requireNonNull(sourceSnapshotId, "sourceSnapshotId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");

        if (review.isFinalized()) {
            return ItemDecisionResult.rejected("Cannot create item decisions on a finalized review");
        }
        if (config.requireAssignmentForMutations() && !review.assignedReviewerUuid().equals(staffUuid)) {
            return ItemDecisionResult.rejected("Only the assigned reviewer can create item decisions on this review");
        }
        if (staffNote != null && staffNote.strip().length() > config.maxItemNoteLength()) {
            return ItemDecisionResult.rejected("Item note exceeds maximum length of " + config.maxItemNoteLength());
        }
        try {
            long decisionCount = decisionRepository.findByReviewId(review.id()).size();
            if (decisionCount >= config.maxItemDecisionsPerClaim()) {
                return ItemDecisionResult.rejected("Maximum item decisions per claim reached: " + config.maxItemDecisionsPerClaim());
            }
            Optional<ClaimReviewItemDecision> existing = decisionRepository
                    .findByReviewIdAndEvidenceRef(review.id(), evidenceItemReference);
            if (existing.isPresent()) {
                return ItemDecisionResult.rejected("An item decision already exists for this evidence item");
            }

            long now = System.currentTimeMillis();
            UUID decisionId = UUID.randomUUID();
            ClaimReviewItemDecision decision = new ClaimReviewItemDecision(
                    decisionId, review.id(), review.claimId(),
                    evidenceItemReference, sourceSnapshotId,
                    originalQuantity, approvedQuantity,
                    outcome, reasonCode,
                    staffNote == null ? "" : staffNote.strip(),
                    now, now, 0, java.util.Map.of()
            );

            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), review.claimId(),
                    ClaimActorType.STAFF, staffUuid,
                    ClaimAction.ITEM_DECISION_CREATED, "",
                    now, review.version() + 1,
                    java.util.Map.of(
                            "reviewId", review.id().toString(),
                            "evidenceItemReference", evidenceItemReference,
                            "outcome", outcome.name()
                    )
            );

            reviewStore.createItemDecision(decision, auditEntry);
            metrics.recordItemDecisionCreated();
            return ItemDecisionResult.success(decision);

        } catch (SQLException e) {
            return ItemDecisionResult.error(e.getMessage());
        }
    }

    public ItemDecisionResult updateItemDecision(
            ClaimReviewItemDecision existing, UUID staffUuid,
            int approvedQuantity,
            ReviewItemOutcome outcome, ReviewReasonCode reasonCode,
            String staffNote
    ) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");

        try {
            long now = System.currentTimeMillis();
            ClaimReviewItemDecision updated = new ClaimReviewItemDecision(
                    existing.id(), existing.reviewId(), existing.claimId(),
                    existing.evidenceItemReference(), existing.sourceSnapshotId(),
                    existing.originalQuantity(), approvedQuantity,
                    outcome, reasonCode,
                    staffNote == null ? "" : staffNote.strip(),
                    existing.createdAt(), now, existing.version(), java.util.Map.of()
            );

            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), existing.claimId(),
                    ClaimActorType.STAFF, staffUuid,
                    ClaimAction.ITEM_DECISION_UPDATED, "",
                    now, existing.version() + 1,
                    java.util.Map.of(
                            "decisionId", existing.id().toString(),
                            "outcome", outcome.name()
                    )
            );

            boolean success = reviewStore.updateItemDecision(
                    updated, existing.version(), auditEntry);

            if (!success) {
                metrics.recordReviewConcurrencyConflict();
                return ItemDecisionResult.concurrencyConflict();
            }

            metrics.recordItemDecisionUpdated();
            return ItemDecisionResult.success(updated);

        } catch (SQLException e) {
            return ItemDecisionResult.error(e.getMessage());
        }
    }

    public FinalizeReviewResult finalizeReview(
            Claim claim, ClaimReview review, UUID staffUuid,
            ReviewOutcome requestedOutcome, String finalSummary
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(requestedOutcome, "requestedOutcome");

        if (review.isFinalized()) {
            return FinalizeReviewResult.rejected("Review is already finalized");
        }
        if (claim.status() != ClaimStatus.UNDER_REVIEW) {
            return FinalizeReviewResult.rejected("Claim must be UNDER_REVIEW to finalize");
        }
        if (config.requireAssignmentForMutations() && !review.assignedReviewerUuid().equals(staffUuid)) {
            return FinalizeReviewResult.rejected("Only the assigned reviewer can finalize this review");
        }

        String summary = finalSummary == null ? "" : finalSummary.strip();
        if (requestedOutcome == ReviewOutcome.REJECTED && summary.isEmpty()) {
            return FinalizeReviewResult.rejected("REJECTED requires a non-blank final summary");
        }
        if (summary.length() > config.maxFinalSummaryLength()) {
            return FinalizeReviewResult.rejected("Final summary exceeds maximum length of " + config.maxFinalSummaryLength());
        }

        try {
            List<ClaimReviewItemDecision> decisions = decisionRepository.findByReviewId(review.id());

            ReviewOutcomePolicy.ValidationResult validation =
                    ReviewOutcomePolicy.validate(requestedOutcome, decisions);
            if (!validation.isAllowed()) {
                metrics.recordReviewRejected();
                return FinalizeReviewResult.rejected(validation.rejectionReason().orElse("Outcome validation failed"));
            }

            ClaimStatus finalClaimStatus = switch (requestedOutcome) {
                case APPROVED -> ClaimStatus.APPROVED;
                case PARTIALLY_APPROVED -> ClaimStatus.PARTIALLY_APPROVED;
                case REJECTED -> ClaimStatus.REJECTED;
            };

            ClaimAction action = switch (requestedOutcome) {
                case APPROVED -> ClaimAction.REVIEW_APPROVED;
                case PARTIALLY_APPROVED -> ClaimAction.REVIEW_PARTIALLY_APPROVED;
                case REJECTED -> ClaimAction.REVIEW_REJECTED;
            };

            long now = System.currentTimeMillis();

            ClaimReviewComment summaryComment = summary.isEmpty() ? null : new ClaimReviewComment(
                    UUID.randomUUID(), review.id(), review.claimId(),
                    staffUuid, ClaimActorType.STAFF,
                    CommentVisibility.CLAIM_PARTICIPANTS, CommentType.FINAL_SUMMARY,
                    summary, now, java.util.Map.of()
            );

            ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                    UUID.randomUUID(), claim.id(),
                    ClaimActorType.STAFF, staffUuid,
                    action, summary,
                    now, claim.version() + 1,
                    java.util.Map.of(
                            "reviewId", review.id().toString(),
                            "outcome", requestedOutcome.name()
                    )
            );

            boolean success = reviewStore.finalizeReview(
                    review.id(), requestedOutcome, summary, now,
                    review.version(),
                    claim.id(), finalClaimStatus, claim.version(),
                    summaryComment, auditEntry);

            if (!success) {
                metrics.recordReviewConcurrencyConflict();
                return FinalizeReviewResult.concurrencyConflict();
            }

            switch (requestedOutcome) {
                case APPROVED -> metrics.recordReviewApproved();
                case PARTIALLY_APPROVED -> metrics.recordReviewPartiallyApproved();
                case REJECTED -> metrics.recordReviewRejected();
            }

            return FinalizeReviewResult.success(requestedOutcome);

        } catch (SQLException e) {
            return FinalizeReviewResult.error(e.getMessage());
        }
    }

    public Optional<ClaimReview> findReviewByClaimId(UUID claimId) throws SQLException {
        return reviewRepository.findByClaimId(claimId);
    }

    public Optional<ClaimReview> findReviewById(UUID reviewId) throws SQLException {
        return reviewRepository.findById(reviewId);
    }

    public List<ClaimReviewComment> findCommentsByClaimId(UUID claimId) throws SQLException {
        return commentRepository.findByClaimId(claimId);
    }

    public List<ClaimReviewComment> findPlayerVisibleComments(UUID claimId) throws SQLException {
        return commentRepository.findByClaimIdAndVisibility(claimId, CommentVisibility.CLAIM_PARTICIPANTS);
    }

    public List<ClaimReviewItemDecision> findDecisionsByReviewId(UUID reviewId) throws SQLException {
        return decisionRepository.findByReviewId(reviewId);
    }

    public List<ClaimReviewItemDecision> findDecisionsByClaimId(UUID claimId) throws SQLException {
        return decisionRepository.findByClaimId(claimId);
    }

    public List<ClaimReview> findReviewsByReviewer(UUID reviewerUuid, int limit) throws SQLException {
        return reviewRepository.findByReviewer(reviewerUuid, limit);
    }

    // ─── Result types ───

    public static final class StartReviewResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final String rejectionReason;
        private final ClaimReview review;

        private StartReviewResult(boolean success, boolean concurrencyConflict, String rejectionReason, ClaimReview review) {
            this.success = success; this.concurrencyConflict = concurrencyConflict;
            this.rejectionReason = rejectionReason; this.review = review;
        }
        static StartReviewResult success(ClaimReview review) { return new StartReviewResult(true, false, null, review); }
        static StartReviewResult rejected(String reason) { return new StartReviewResult(false, false, reason, null); }
        static StartReviewResult concurrencyConflict() { return new StartReviewResult(false, true, "Concurrency conflict", null); }
        static StartReviewResult error(String msg) { return new StartReviewResult(false, false, "Database error: " + msg, null); }

        public boolean isSuccess() { return success; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
        public Optional<ClaimReview> review() { return Optional.ofNullable(review); }
    }

    public static final class TakeoverResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final String rejectionReason;
        private TakeoverResult(boolean success, boolean concurrencyConflict, String rejectionReason) {
            this.success = success; this.concurrencyConflict = concurrencyConflict; this.rejectionReason = rejectionReason;
        }
        static TakeoverResult success() { return new TakeoverResult(true, false, null); }
        static TakeoverResult rejected(String reason) { return new TakeoverResult(false, false, reason); }
        static TakeoverResult concurrencyConflict() { return new TakeoverResult(false, true, "Concurrency conflict"); }
        static TakeoverResult error(String msg) { return new TakeoverResult(false, false, "Database error: " + msg); }
        public boolean isSuccess() { return success; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
    }

    public static final class AddNoteResult {
        private final boolean success;
        private final String rejectionReason;
        private AddNoteResult(boolean success, String rejectionReason) { this.success = success; this.rejectionReason = rejectionReason; }
        static AddNoteResult success() { return new AddNoteResult(true, null); }
        static AddNoteResult rejected(String reason) { return new AddNoteResult(false, reason); }
        static AddNoteResult error(String msg) { return new AddNoteResult(false, "Database error: " + msg); }
        public boolean isSuccess() { return success; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
    }

    public static final class RequestInfoResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final String rejectionReason;
        private RequestInfoResult(boolean success, boolean concurrencyConflict, String rejectionReason) {
            this.success = success; this.concurrencyConflict = concurrencyConflict; this.rejectionReason = rejectionReason;
        }
        static RequestInfoResult success() { return new RequestInfoResult(true, false, null); }
        static RequestInfoResult rejected(String reason) { return new RequestInfoResult(false, false, reason); }
        static RequestInfoResult concurrencyConflict() { return new RequestInfoResult(false, true, "Concurrency conflict"); }
        static RequestInfoResult error(String msg) { return new RequestInfoResult(false, false, "Database error: " + msg); }
        public boolean isSuccess() { return success; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
    }

    public static final class PlayerResponseResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final String rejectionReason;
        private PlayerResponseResult(boolean success, boolean concurrencyConflict, String rejectionReason) {
            this.success = success; this.concurrencyConflict = concurrencyConflict; this.rejectionReason = rejectionReason;
        }
        static PlayerResponseResult success() { return new PlayerResponseResult(true, false, null); }
        static PlayerResponseResult rejected(String reason) { return new PlayerResponseResult(false, false, reason); }
        static PlayerResponseResult concurrencyConflict() { return new PlayerResponseResult(false, true, "Concurrency conflict"); }
        static PlayerResponseResult error(String msg) { return new PlayerResponseResult(false, false, "Database error: " + msg); }
        public boolean isSuccess() { return success; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
    }

    public static final class ItemDecisionResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final String rejectionReason;
        private final ClaimReviewItemDecision decision;
        private ItemDecisionResult(boolean success, boolean concurrencyConflict, String rejectionReason, ClaimReviewItemDecision decision) {
            this.success = success; this.concurrencyConflict = concurrencyConflict;
            this.rejectionReason = rejectionReason; this.decision = decision;
        }
        static ItemDecisionResult success(ClaimReviewItemDecision decision) { return new ItemDecisionResult(true, false, null, decision); }
        static ItemDecisionResult rejected(String reason) { return new ItemDecisionResult(false, false, reason, null); }
        static ItemDecisionResult concurrencyConflict() { return new ItemDecisionResult(false, true, "Concurrency conflict", null); }
        static ItemDecisionResult error(String msg) { return new ItemDecisionResult(false, false, "Database error: " + msg, null); }
        public boolean isSuccess() { return success; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
        public Optional<ClaimReviewItemDecision> decision() { return Optional.ofNullable(decision); }
    }

    public static final class FinalizeReviewResult {
        private final boolean success;
        private final boolean concurrencyConflict;
        private final String rejectionReason;
        private final ReviewOutcome outcome;
        private FinalizeReviewResult(boolean success, boolean concurrencyConflict, String rejectionReason, ReviewOutcome outcome) {
            this.success = success; this.concurrencyConflict = concurrencyConflict;
            this.rejectionReason = rejectionReason; this.outcome = outcome;
        }
        static FinalizeReviewResult success(ReviewOutcome outcome) { return new FinalizeReviewResult(true, false, null, outcome); }
        static FinalizeReviewResult rejected(String reason) { return new FinalizeReviewResult(false, false, reason, null); }
        static FinalizeReviewResult concurrencyConflict() { return new FinalizeReviewResult(false, true, "Concurrency conflict", null); }
        static FinalizeReviewResult error(String msg) { return new FinalizeReviewResult(false, false, "Database error: " + msg, null); }
        public boolean isSuccess() { return success; }
        public boolean isConcurrencyConflict() { return concurrencyConflict; }
        public Optional<String> rejectionReason() { return Optional.ofNullable(rejectionReason); }
        public Optional<ReviewOutcome> outcome() { return Optional.ofNullable(outcome); }
    }
}
