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
import io.github.skyblueheat.echoclaims.domain.review.CommentType;
import io.github.skyblueheat.echoclaims.domain.review.CommentVisibility;
import io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SqliteClaimReviewStore} operations are atomic:
 * if any step fails, the entire transaction rolls back leaving no orphan rows.
 */
class ClaimReviewStoreAtomicityTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private ClaimReviewRepository reviewRepository;
    private ClaimReviewCommentRepository commentRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("review_atomicity.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        reviewRepository = new SqliteClaimReviewRepository(databaseManager);
        commentRepository = new SqliteClaimReviewCommentRepository(databaseManager);
    }

    private Claim seedClaim() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        new SqliteInventorySnapshotRepository(databaseManager).insert(new InventorySnapshot(
                snapshotId, playerUuid, CaptureReason.PRE_DEATH,
                10_000L, "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        ));
        UUID incidentId = UUID.randomUUID();
        new SqliteIncidentRepository(databaseManager).insert(new Incident(
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
    void startReviewRollsBackOnDuplicateReviewInsert() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        Optional<Claim> afterFirst = claimStore.findClaimById(claim.id());
        assertTrue(afterFirst.isPresent());
        assertEquals(ClaimStatus.UNDER_REVIEW, afterFirst.get().status());
        int currentVersion = afterFirst.get().version();

        ClaimReview duplicateReview = new ClaimReview(
                UUID.randomUUID(), claim.id(), UUID.randomUUID(),
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );

        assertThrows(SQLException.class, () ->
                reviewStore.startReview(duplicateReview, claim.id(), currentVersion,
                        auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, currentVersion + 1)));

        Optional<Claim> unchanged = claimStore.findClaimById(claim.id());
        assertTrue(unchanged.isPresent());
        assertEquals(ClaimStatus.UNDER_REVIEW, unchanged.get().status());
        assertEquals(currentVersion, unchanged.get().version());

        long reviewCount = reviewRepository.count();
        assertEquals(1, reviewCount);
    }

    @Test
    void finalizeReviewRollsBackOnDuplicateCommentId() throws Exception {
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

        UUID duplicateCommentId = UUID.randomUUID();
        ClaimReviewComment comment1 = new ClaimReviewComment(
                duplicateCommentId, review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "First note", now, Map.of()
        );
        reviewStore.addInternalNote(comment1,
                auditEntry(claim.id(), ClaimAction.INTERNAL_NOTE_ADDED, 1));

        ClaimReviewComment duplicateComment = new ClaimReviewComment(
                duplicateCommentId, review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.FINAL_SUMMARY,
                "Final summary", now, Map.of()
        );

        assertThrows(SQLException.class, () ->
                reviewStore.finalizeReview(
                        review.id(), ReviewOutcome.APPROVED, "Summary",
                        now, review.version(),
                        claim.id(), ClaimStatus.APPROVED, underReview.get().version(),
                        duplicateComment,
                        auditEntry(claim.id(), ClaimAction.REVIEW_APPROVED, 2)));

        Optional<ClaimReview> stillOpen = reviewRepository.findById(review.id());
        assertTrue(stillOpen.isPresent());
        assertEquals(ReviewState.OPEN, stillOpen.get().reviewState());

        Optional<Claim> claimStillUnderReview = claimStore.findClaimById(claim.id());
        assertTrue(claimStillUnderReview.isPresent());
        assertEquals(ClaimStatus.UNDER_REVIEW, claimStillUnderReview.get().status());
    }

    @Test
    void requestInformationRollsBackOnDuplicateCommentId() throws Exception {
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

        UUID duplicateId = UUID.randomUUID();
        ClaimReviewComment firstComment = new ClaimReviewComment(
                duplicateId, review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "Note", now, Map.of()
        );
        reviewStore.addInternalNote(firstComment,
                auditEntry(claim.id(), ClaimAction.INTERNAL_NOTE_ADDED, 1));

        ClaimReviewComment duplicateComment = new ClaimReviewComment(
                duplicateId, review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.INFORMATION_REQUEST,
                "Question", now, Map.of()
        );

        assertThrows(SQLException.class, () ->
                reviewStore.requestInformation(
                        duplicateComment, claim.id(), underReview.get().version(),
                        auditEntry(claim.id(), ClaimAction.INFORMATION_REQUESTED, 2)));

        Optional<Claim> stillUnderReview = claimStore.findClaimById(claim.id());
        assertTrue(stillUnderReview.isPresent());
        assertEquals(ClaimStatus.UNDER_REVIEW, stillUnderReview.get().status());
    }

    @Test
    void addInternalNoteDoesNotAffectClaimStatus() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        Optional<Claim> before = claimStore.findClaimById(claim.id());
        assertTrue(before.isPresent());
        int versionBefore = before.get().version();

        ClaimReviewComment comment = new ClaimReviewComment(
                UUID.randomUUID(), review.id(), claim.id(),
                staffUuid, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "Note", now, Map.of()
        );
        reviewStore.addInternalNote(comment,
                auditEntry(claim.id(), ClaimAction.INTERNAL_NOTE_ADDED, 1));

        Optional<Claim> after = claimStore.findClaimById(claim.id());
        assertTrue(after.isPresent());
        assertEquals(versionBefore, after.get().version());
        assertEquals(ClaimStatus.UNDER_REVIEW, after.get().status());

        assertEquals(1, commentRepository.findByReviewId(review.id()).size());
    }

    @Test
    void createItemDecisionDoesNotAffectClaimStatus() throws Exception {
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

        Optional<Claim> before = claimStore.findClaimById(claim.id());
        assertTrue(before.isPresent());
        int versionBefore = before.get().version();

        io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision decision =
                new io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision(
                        UUID.randomUUID(), review.id(), claim.id(),
                        "snap:0", snapshotId,
                        10, 10,
                        io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome.APPROVED,
                        io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode.EVIDENCE_CONFIRMS_LOSS,
                        "", now, now, 0, Map.of()
                );
        reviewStore.createItemDecision(decision,
                auditEntry(claim.id(), ClaimAction.ITEM_DECISION_CREATED, 1));

        Optional<Claim> after = claimStore.findClaimById(claim.id());
        assertTrue(after.isPresent());
        assertEquals(versionBefore, after.get().version());
    }
}
