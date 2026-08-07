package io.github.skyblueheat.echoclaims.domain.review;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, append-only review comment.
 *
 * <p>Comments are never edited or deleted. Corrections must be added as a
 * new comment. Internal staff comments ({@code INTERNAL_STAFF} visibility)
 * must never be visible to ordinary players.</p>
 */
public record ClaimReviewComment(
        UUID id,
        UUID reviewId,
        UUID claimId,
        UUID actorUuid,
        ClaimActorType actorType,
        CommentVisibility visibility,
        CommentType commentType,
        String body,
        long createdAt,
        Map<String, String> metadata
) {

    public ClaimReviewComment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(actorUuid, "actorUuid");
        actorType = Objects.requireNonNull(actorType, "actorType");
        visibility = Objects.requireNonNull(visibility, "visibility");
        commentType = Objects.requireNonNull(commentType, "commentType");
        body = Objects.requireNonNullElse(body, "").strip();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("body cannot be blank");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt cannot be negative");
        }
        metadata = copyMetadata(metadata);
    }

    public boolean isInternal() {
        return visibility == CommentVisibility.INTERNAL_STAFF;
    }

    public boolean isPlayerVisible() {
        return visibility == CommentVisibility.CLAIM_PARTICIPANTS;
    }

    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }

    private static Map<String, String> copyMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            copy.put(entry.getKey().strip(), Objects.requireNonNullElse(entry.getValue(), "").strip());
        }
        return Map.copyOf(copy);
    }
}
