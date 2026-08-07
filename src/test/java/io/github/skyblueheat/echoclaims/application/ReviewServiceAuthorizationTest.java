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
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment;
import io.github.skyblueheat.echoclaims.domain.review.CommentType;
import io.github.skyblueheat.echoclaims.domain.review.CommentVisibility;
import io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewCommentRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewStore;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.IncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewCommentRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteIncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteInventorySnapshotRepository;
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

/**
 * Tests authorization enforcement in {@link ReviewService}:
 * - assigned-reviewer-only mutations when requireAssignmentForMutations is true
 * - takeover requires a non-blank reason
 * - REJECTED finalization requires a non-blank summary
 * - comment visibility filtering for player access
 */
class ReviewServiceAuthorizationTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private ClaimReviewRepository reviewRepository;
    private ClaimReviewCommentRepository commentRepository;
    private ClaimReviewItemDecisionRepository decisionRepository;
    private IncidentRepository incidentRepository;
    private InventorySnapshotRepository snapshotRepository;
    private ReviewService reviewService;
    private ReviewServiceConfig config;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("review_auth.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        reviewRepository = new SqliteClaimReviewRepository(databaseManager);
        commentRepository = new SqliteClaimReviewCommentRepository(databaseManager);
        decisionRepository = new SqliteClaimReviewItemDecisionRepository(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
        config = ReviewServiceConfig.defaults();
        reviewService = new ReviewService(
                reviewRepository, commentRepository, decisionRepository,
                reviewStore, new ReviewMetrics(), config
        );
    }

    private Claim seedClaim() throws Exception {
        UUID playerUuid = UUID.randomUUID();
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
        UUID claimId = UUID.randomUUID();
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
        return claim;
    }

    private Claim startReviewForClaim(Claim claim, UUID staffUuid) throws Exception {
        ReviewService.StartReviewResult result = reviewService.startReview(claim, staffUuid);
        assertTrue(result.isSuccess());
        Optional<Claim> updated = claimStore.findClaimById(claim.id());
        return updated.orElseThrow();
    }

    private ClaimReview getReview(UUID claimId) throws Exception {
        return reviewRepository.findByClaimId(claimId).orElseThrow();
    }

    // --- Assigned reviewer enforcement ---

    @Test
    void nonAssignedReviewerCannotAddNote() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.AddNoteResult result = reviewService.addInternalNote(review, staffB, "note");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().isPresent());
        assertTrue(result.rejectionReason().get().contains("assigned reviewer"));
    }

    @Test
    void assignedReviewerCanAddNote() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.AddNoteResult result = reviewService.addInternalNote(review, staffA, "note");
        assertTrue(result.isSuccess());
    }

    @Test
    void nonAssignedReviewerCannotRequestInfo() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.RequestInfoResult result = reviewService.requestInformation(claim, review, staffB, "question?");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("assigned reviewer"));
    }

    @Test
    void nonAssignedReviewerCannotCreateItemDecision() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.ItemDecisionResult result = reviewService.createItemDecision(
                review, staffB, "snap:0", UUID.randomUUID(),
                10, 10, ReviewItemOutcome.APPROVED,
                ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS, "");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("assigned reviewer"));
    }

    @Test
    void nonAssignedReviewerCannotFinalize() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.FinalizeReviewResult result = reviewService.finalizeReview(
                claim, review, staffB, ReviewOutcome.REJECTED, "summary");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("assigned reviewer"));
    }

    @Test
    void assignedReviewerCanFinalize() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.FinalizeReviewResult result = reviewService.finalizeReview(
                claim, review, staffA, ReviewOutcome.REJECTED, "Rejected summary");
        assertTrue(result.isSuccess());
    }

    // --- Takeover reason ---

    @Test
    void takeoverWithBlankReasonIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.TakeoverResult result = reviewService.takeoverReview(review, staffB, "   ");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("blank"));
    }

    @Test
    void takeoverWithNullReasonIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.TakeoverResult result = reviewService.takeoverReview(review, staffB, null);
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("blank"));
    }

    @Test
    void takeoverWithReasonSucceeds() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.TakeoverResult result = reviewService.takeoverReview(review, staffB, "Staff A is unavailable");
        assertTrue(result.isSuccess());
    }

    @Test
    void takeoverSelfIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.TakeoverResult result = reviewService.takeoverReview(review, staffA, "reason");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("already"));
    }

    @Test
    void takeoverOnFinalizedReviewIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        reviewService.finalizeReview(claim, review, staffA, ReviewOutcome.REJECTED, "Done");
        ClaimReview finalized = getReview(claim.id());

        ReviewService.TakeoverResult result = reviewService.takeoverReview(finalized, staffB, "reason");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("finalized"));
    }

    // --- REJECTED summary ---

    @Test
    void rejectedWithoutSummaryIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.FinalizeReviewResult result = reviewService.finalizeReview(
                claim, review, staffA, ReviewOutcome.REJECTED, "");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("REJECTED requires a non-blank"));
    }

    @Test
    void rejectedWithBlankSummaryIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.FinalizeReviewResult result = reviewService.finalizeReview(
                claim, review, staffA, ReviewOutcome.REJECTED, "   ");
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("REJECTED requires a non-blank"));
    }

    @Test
    void rejectedWithNullSummaryIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewService.FinalizeReviewResult result = reviewService.finalizeReview(
                claim, review, staffA, ReviewOutcome.REJECTED, null);
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("REJECTED requires a non-blank"));
    }

    @Test
    void approvedWithoutSummaryIsAllowed() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        reviewService.createItemDecision(review, staffA, "snap:0", snapshotId,
                10, 10, ReviewItemOutcome.APPROVED,
                ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS, "");

        ReviewService.FinalizeReviewResult result = reviewService.finalizeReview(
                claim, review, staffA, ReviewOutcome.APPROVED, "");
        assertTrue(result.isSuccess());
    }

    // --- Comment visibility / privacy ---

    @Test
    void internalCommentsNotVisibleToPlayer() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        reviewService.addInternalNote(review, staffA, "Secret internal note");

        List<ClaimReviewComment> playerVisible = reviewService.findPlayerVisibleComments(claim.id());
        assertTrue(playerVisible.isEmpty());

        List<ClaimReviewComment> all = reviewService.findCommentsByClaimId(claim.id());
        assertEquals(1, all.size());
        assertTrue(all.get(0).isInternal());
    }

    @Test
    void informationRequestVisibleToPlayer() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        reviewService.requestInformation(claim, review, staffA, "What happened?");

        List<ClaimReviewComment> playerVisible = reviewService.findPlayerVisibleComments(claim.id());
        assertEquals(1, playerVisible.size());
        assertEquals("What happened?", playerVisible.get(0).body());
        assertFalse(playerVisible.get(0).isInternal());
    }

    @Test
    void playerResponseVisibleToPlayer() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        reviewService.requestInformation(claim, review, staffA, "Question?");
        claim = claimStore.findClaimById(claim.id()).orElseThrow();

        reviewService.recordPlayerResponse(claim, review, claim.playerUuid(), "My answer");

        List<ClaimReviewComment> playerVisible = reviewService.findPlayerVisibleComments(claim.id());
        assertEquals(2, playerVisible.size());
    }

    // --- Config-driven limits ---

    @Test
    void noteExceedingMaxLengthIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        String longNote = "x".repeat(config.maxInternalNoteLength() + 1);
        ReviewService.AddNoteResult result = reviewService.addInternalNote(review, staffA, longNote);
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("maximum length"));
    }

    @Test
    void questionExceedingMaxLengthIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        String longQuestion = "x".repeat(config.maxQuestionLength() + 1);
        ReviewService.RequestInfoResult result = reviewService.requestInformation(claim, review, staffA, longQuestion);
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("maximum length"));
    }

    @Test
    void summaryExceedingMaxLengthIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        claim = startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        String longSummary = "x".repeat(config.maxFinalSummaryLength() + 1);
        ReviewService.FinalizeReviewResult result = reviewService.finalizeReview(
                claim, review, staffA, ReviewOutcome.REJECTED, longSummary);
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("maximum length"));
    }

    @Test
    void itemNoteExceedingMaxLengthIsRejected() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        String longNote = "x".repeat(config.maxItemNoteLength() + 1);
        ReviewService.ItemDecisionResult result = reviewService.createItemDecision(
                review, staffA, "snap:0", UUID.randomUUID(),
                10, 0, ReviewItemOutcome.REJECTED,
                ReviewReasonCode.ITEM_NOT_SUPPORTED_BY_EVIDENCE, longNote);
        assertFalse(result.isSuccess());
        assertTrue(result.rejectionReason().get().contains("maximum length"));
    }

    // --- requireAssignmentForMutations = false ---

    @Test
    void nonAssignedReviewerCanAddNoteWhenEnforcementDisabled() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        startReviewForClaim(claim, staffA);
        ClaimReview review = getReview(claim.id());

        ReviewServiceConfig relaxedConfig = new ReviewServiceConfig(
                false, 1_000, 1_000, 1_000, 2_000, 500, 100);
        ReviewService relaxedService = new ReviewService(
                reviewRepository, commentRepository, decisionRepository,
                reviewStore, new ReviewMetrics(), relaxedConfig);

        ReviewService.AddNoteResult result = relaxedService.addInternalNote(review, staffB, "note from B");
        assertTrue(result.isSuccess());
    }
}
