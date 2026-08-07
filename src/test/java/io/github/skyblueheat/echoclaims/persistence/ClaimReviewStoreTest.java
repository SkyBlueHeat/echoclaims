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
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision;
import io.github.skyblueheat.echoclaims.domain.review.CommentType;
import io.github.skyblueheat.echoclaims.domain.review.CommentVisibility;
import io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
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

class ClaimReviewStoreTest {

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

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("review_store_test.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        reviewRepository = new SqliteClaimReviewRepository(databaseManager);
        commentRepository = new SqliteClaimReviewCommentRepository(databaseManager);
        decisionRepository = new SqliteClaimReviewItemDecisionRepository(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
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

    private ClaimAuditEntry auditEntry(UUID claimId, ClaimAction action, int version) {
        return new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.STAFF,
                UUID.randomUUID(), action, "test",
                System.currentTimeMillis(), version, Map.of()
        );
    }

    @Test
    void startReviewInsertsReviewAndUpdatesClaimStatus() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        ClaimAuditEntry audit = auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1);

        boolean success = reviewStore.startReview(review, claim.id(), claim.version(), audit);
        assertTrue(success);

        Optional<Claim> updated = claimStore.findClaimById(claim.id());
        assertTrue(updated.isPresent());
        assertEquals(ClaimStatus.UNDER_REVIEW, updated.get().status());
        assertEquals(1, updated.get().version());

        Optional<ClaimReview> found = reviewRepository.findByClaimId(claim.id());
        assertTrue(found.isPresent());
        assertEquals(staffUuid, found.get().assignedReviewerUuid());
    }

    @Test
    void startReviewFailsOnStaleClaimVersion() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        ClaimAuditEntry audit = auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1);

        boolean success = reviewStore.startReview(review, claim.id(), 999, audit);
        assertFalse(success);

        Optional<Claim> unchanged = claimStore.findClaimById(claim.id());
        assertTrue(unchanged.isPresent());
        assertEquals(ClaimStatus.SUBMITTED, unchanged.get().status());

        Optional<ClaimReview> noReview = reviewRepository.findByClaimId(claim.id());
        assertFalse(noReview.isPresent());
    }

    @Test
    void takeoverReviewUpdatesReviewer() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffA,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        boolean success = reviewStore.takeoverReview(
                review.id(), staffB, review.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_TAKEN_OVER, 2));
        assertTrue(success);

        Optional<ClaimReview> updated = reviewRepository.findById(review.id());
        assertTrue(updated.isPresent());
        assertEquals(staffB, updated.get().assignedReviewerUuid());
        assertEquals(1, updated.get().version());
    }

    @Test
    void takeoverReviewFailsOnStaleVersion() throws Exception {
        Claim claim = seedClaim();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffA,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        boolean success = reviewStore.takeoverReview(
                review.id(), staffB, 999,
                auditEntry(claim.id(), ClaimAction.REVIEW_TAKEN_OVER, 2));
        assertFalse(success);
    }

    @Test
    void addInternalNotePersistsComment() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        ClaimReviewComment comment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "Internal note", now, Map.of()
        );
        reviewStore.addInternalNote(comment, auditEntry(claim.id(), ClaimAction.INTERNAL_NOTE_ADDED, 1));

        List<ClaimReviewComment> comments = commentRepository.findByReviewId(review.id());
        assertEquals(1, comments.size());
        assertEquals("Internal note", comments.get(0).body());
        assertTrue(comments.get(0).isInternal());
    }

    @Test
    void requestInformationTransitionsToWaitingForPlayer() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        Optional<Claim> underReview = claimStore.findClaimById(claim.id());
        assertTrue(underReview.isPresent());

        ClaimReviewComment comment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.INFORMATION_REQUEST,
                "Please provide more info", now, Map.of()
        );
        boolean success = reviewStore.requestInformation(
                comment, claim.id(), underReview.get().version(),
                auditEntry(claim.id(), ClaimAction.INFORMATION_REQUESTED, 2));
        assertTrue(success);

        Optional<Claim> waiting = claimStore.findClaimById(claim.id());
        assertTrue(waiting.isPresent());
        assertEquals(ClaimStatus.WAITING_FOR_PLAYER, waiting.get().status());
    }

    @Test
    void recordPlayerResponseTransitionsBackToUnderReview() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        UUID playerUuid = claim.playerUuid();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        Optional<Claim> underReview = claimStore.findClaimById(claim.id());
        assertTrue(underReview.isPresent());

        ClaimReviewComment requestComment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.INFORMATION_REQUEST,
                "Question?", now, Map.of()
        );
        reviewStore.requestInformation(requestComment, claim.id(),
                underReview.get().version(),
                auditEntry(claim.id(), ClaimAction.INFORMATION_REQUESTED, 2));

        Optional<Claim> waiting = claimStore.findClaimById(claim.id());
        assertTrue(waiting.isPresent());

        ClaimReviewComment responseComment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                playerUuid, ClaimActorType.PLAYER,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.PLAYER_RESPONSE,
                "My response", now, Map.of()
        );
        boolean success = reviewStore.recordPlayerResponse(
                responseComment, claim.id(), waiting.get().version(),
                auditEntry(claim.id(), ClaimAction.PLAYER_RESPONDED, 3));
        assertTrue(success);

        Optional<Claim> backToReview = claimStore.findClaimById(claim.id());
        assertTrue(backToReview.isPresent());
        assertEquals(ClaimStatus.UNDER_REVIEW, backToReview.get().status());
    }

    @Test
    void createItemDecisionPersistsDecision() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        ClaimReviewItemDecision decision = new ClaimReviewItemDecision(
                UUID.randomUUID(), review.id(), claim.id(),
                "snap:0", snapshotId,
                10, 10, ReviewItemOutcome.APPROVED,
                ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", now, now, 0, Map.of()
        );
        reviewStore.createItemDecision(decision,
                auditEntry(claim.id(), ClaimAction.ITEM_DECISION_CREATED, 1));

        List<ClaimReviewItemDecision> decisions = decisionRepository.findByReviewId(review.id());
        assertEquals(1, decisions.size());
        assertEquals(10, decisions.get(0).approvedQuantity());
    }

    @Test
    void updateItemDecisionUpdatesExistingDecision() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        ClaimReviewItemDecision decision = new ClaimReviewItemDecision(
                UUID.randomUUID(), review.id(), claim.id(),
                "snap:0", snapshotId,
                10, 10, ReviewItemOutcome.APPROVED,
                ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", now, now, 0, Map.of()
        );
        reviewStore.createItemDecision(decision,
                auditEntry(claim.id(), ClaimAction.ITEM_DECISION_CREATED, 1));

        ClaimReviewItemDecision updated = new ClaimReviewItemDecision(
                decision.id(), review.id(), claim.id(),
                "snap:0", snapshotId,
                10, 5, ReviewItemOutcome.PARTIALLY_APPROVED,
                ReviewReasonCode.EVIDENCE_PARTIALLY_CONFIRMS_LOSS,
                "Changed mind", now, now, 1, Map.of()
        );
        boolean success = reviewStore.updateItemDecision(updated, 0,
                auditEntry(claim.id(), ClaimAction.ITEM_DECISION_UPDATED, 1));
        assertTrue(success);

        Optional<ClaimReviewItemDecision> found = decisionRepository
                .findByReviewIdAndEvidenceRef(review.id(), "snap:0");
        assertTrue(found.isPresent());
        assertEquals(5, found.get().approvedQuantity());
        assertEquals(ReviewItemOutcome.PARTIALLY_APPROVED, found.get().outcome());
    }

    @Test
    void updateItemDecisionFailsOnStaleVersion() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        ClaimReviewItemDecision decision = new ClaimReviewItemDecision(
                UUID.randomUUID(), review.id(), claim.id(),
                "snap:0", snapshotId,
                10, 10, ReviewItemOutcome.APPROVED,
                ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                "", now, now, 0, Map.of()
        );
        reviewStore.createItemDecision(decision,
                auditEntry(claim.id(), ClaimAction.ITEM_DECISION_CREATED, 1));

        ClaimReviewItemDecision updated = new ClaimReviewItemDecision(
                decision.id(), review.id(), claim.id(),
                "snap:0", snapshotId,
                10, 0, ReviewItemOutcome.REJECTED,
                ReviewReasonCode.ITEM_NOT_SUPPORTED_BY_EVIDENCE,
                "", now, now, 1, Map.of()
        );
        boolean success = reviewStore.updateItemDecision(updated, 999,
                auditEntry(claim.id(), ClaimAction.ITEM_DECISION_UPDATED, 1));
        assertFalse(success);
    }

    @Test
    void finalizeReviewUpdatesReviewAndClaimStatus() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        Optional<Claim> underReview = claimStore.findClaimById(claim.id());
        assertTrue(underReview.isPresent());

        ClaimReviewComment summaryComment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.FINAL_SUMMARY,
                "All items approved", now, Map.of()
        );

        boolean success = reviewStore.finalizeReview(
                review.id(), ReviewOutcome.APPROVED, "All items approved",
                now, review.version(),
                claim.id(), ClaimStatus.APPROVED, underReview.get().version(),
                summaryComment,
                auditEntry(claim.id(), ClaimAction.REVIEW_APPROVED, 2));
        assertTrue(success);

        Optional<ClaimReview> finalized = reviewRepository.findById(review.id());
        assertTrue(finalized.isPresent());
        assertEquals(ReviewState.FINALIZED, finalized.get().reviewState());
        assertEquals(ReviewOutcome.APPROVED, finalized.get().finalOutcome());

        Optional<Claim> approvedClaim = claimStore.findClaimById(claim.id());
        assertTrue(approvedClaim.isPresent());
        assertEquals(ClaimStatus.APPROVED, approvedClaim.get().status());
        assertTrue(approvedClaim.get().isTerminal());
    }

    @Test
    void finalizeReviewFailsOnStaleReviewVersion() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        Optional<Claim> underReview = claimStore.findClaimById(claim.id());
        assertTrue(underReview.isPresent());

        boolean success = reviewStore.finalizeReview(
                review.id(), ReviewOutcome.REJECTED, "Rejected",
                now, 999,
                claim.id(), ClaimStatus.REJECTED, underReview.get().version(),
                null,
                auditEntry(claim.id(), ClaimAction.REVIEW_REJECTED, 2));
        assertFalse(success);

        Optional<ClaimReview> stillOpen = reviewRepository.findById(review.id());
        assertTrue(stillOpen.isPresent());
        assertEquals(ReviewState.OPEN, stillOpen.get().reviewState());
    }

    @Test
    void finalizeReviewFailsOnStaleClaimVersion() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        boolean success = reviewStore.finalizeReview(
                review.id(), ReviewOutcome.REJECTED, "Rejected",
                now, review.version(),
                claim.id(), ClaimStatus.REJECTED, 999,
                null,
                auditEntry(claim.id(), ClaimAction.REVIEW_REJECTED, 2));
        assertFalse(success);
    }

    @Test
    void commentVisibilityFiltersCorrectly() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        ClaimReviewComment internalComment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "Secret staff note", now, Map.of()
        );
        reviewStore.addInternalNote(internalComment,
                auditEntry(claim.id(), ClaimAction.INTERNAL_NOTE_ADDED, 1));

        ClaimReviewComment visibleComment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.INFORMATION_REQUEST,
                "Visible question", now, Map.of()
        );
        reviewStore.addInternalNote(visibleComment,
                auditEntry(claim.id(), ClaimAction.INTERNAL_NOTE_ADDED, 1));

        List<ClaimReviewComment> allComments = commentRepository.findByClaimId(claim.id());
        assertEquals(2, allComments.size());

        List<ClaimReviewComment> visibleOnly = commentRepository
                .findByClaimIdAndVisibility(claim.id(), CommentVisibility.CLAIM_PARTICIPANTS);
        assertEquals(1, visibleOnly.size());
        assertEquals("Visible question", visibleOnly.get(0).body());

        List<ClaimReviewComment> internalOnly = commentRepository
                .findByClaimIdAndVisibility(claim.id(), CommentVisibility.INTERNAL_STAFF);
        assertEquals(1, internalOnly.size());
        assertEquals("Secret staff note", internalOnly.get(0).body());
    }
}
