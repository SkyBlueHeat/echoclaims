package io.github.skyblueheat.echoclaims.domain.review;

/**
 * The type of a {@link ClaimReviewComment}.
 *
 * <p>Only implemented behavior types are included. Corrections must be added
 * as a new comment; editing and deletion are not supported.</p>
 */
public enum CommentType {
    INTERNAL_NOTE,
    INFORMATION_REQUEST,
    PLAYER_RESPONSE,
    FINAL_SUMMARY
}
