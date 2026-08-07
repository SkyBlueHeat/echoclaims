package io.github.skyblueheat.echoclaims.domain.review;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimReviewCommentTest {

    private static final UUID COMMENT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID ACTOR_UUID = UUID.randomUUID();

    @Test
    void createsValidInternalNote() {
        assertDoesNotThrow(() -> new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "Internal note body", 10_000L, Map.of()
        ));
    }

    @Test
    void createsValidPlayerResponse() {
        assertDoesNotThrow(() -> new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.PLAYER,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.PLAYER_RESPONSE,
                "Player response body", 10_000L, Map.of()
        ));
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class, () -> new ClaimReviewComment(
                null, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "body", 10_000L, Map.of()
        ));
    }

    @Test
    void rejectsNullReviewId() {
        assertThrows(NullPointerException.class, () -> new ClaimReviewComment(
                COMMENT_ID, null, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "body", 10_000L, Map.of()
        ));
    }

    @Test
    void rejectsNullActorUuid() {
        assertThrows(NullPointerException.class, () -> new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                null, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "body", 10_000L, Map.of()
        ));
    }

    @Test
    void rejectsBlankBody() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "   ", 10_000L, Map.of()
        ));
    }

    @Test
    void rejectsNullBody() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                null, 10_000L, Map.of()
        ));
    }

    @Test
    void rejectsNegativeCreatedAt() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "body", -1L, Map.of()
        ));
    }

    @Test
    void stripsBody() {
        ClaimReviewComment comment = new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "  body text  ", 10_000L, Map.of()
        );
        assertEquals("body text", comment.body());
    }

    @Test
    void isInternalReturnsTrueForInternalStaffVisibility() {
        ClaimReviewComment comment = new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "body", 10_000L, Map.of()
        );
        assertTrue(comment.isInternal());
        assertFalse(comment.isPlayerVisible());
    }

    @Test
    void isPlayerVisibleReturnsTrueForClaimParticipantsVisibility() {
        ClaimReviewComment comment = new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.PLAYER,
                CommentVisibility.CLAIM_PARTICIPANTS, CommentType.PLAYER_RESPONSE,
                "body", 10_000L, Map.of()
        );
        assertTrue(comment.isPlayerVisible());
        assertFalse(comment.isInternal());
    }

    @Test
    void metadataIsDefensivelyCopied() {
        Map<String, String> mutable = new java.util.HashMap<>(Map.of("k", "v"));
        ClaimReviewComment comment = new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "body", 10_000L, mutable
        );
        mutable.put("k2", "v2");
        assertEquals(1, comment.metadata().size());
        assertTrue(comment.hasMetadata());
    }

    @Test
    void nullMetadataBecomesEmptyMap() {
        ClaimReviewComment comment = new ClaimReviewComment(
                COMMENT_ID, REVIEW_ID, CLAIM_ID,
                ACTOR_UUID, ClaimActorType.STAFF,
                CommentVisibility.INTERNAL_STAFF, CommentType.INTERNAL_NOTE,
                "body", 10_000L, null
        );
        assertFalse(comment.hasMetadata());
        assertTrue(comment.metadata().isEmpty());
    }
}
