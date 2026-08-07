package io.github.skyblueheat.echoclaims.domain.review;

/**
 * Visibility of a {@link ClaimReviewComment}.
 *
 * <p>{@code INTERNAL_STAFF} comments are never visible to ordinary players.
 * {@code CLAIM_PARTICIPANTS} comments are visible to both the claimant and
 * assigned staff.</p>
 */
public enum CommentVisibility {
    INTERNAL_STAFF,
    CLAIM_PARTICIPANTS
}
