package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment;
import io.github.skyblueheat.echoclaims.domain.review.CommentVisibility;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ClaimReviewComment} persistence.
 *
 * <p>Comments are append-only. Implementations must not support editing or
 * deletion. Implementations are called exclusively from EchoClaims worker
 * threads, never from the server main thread.</p>
 */
public interface ClaimReviewCommentRepository {

    void insert(ClaimReviewComment comment) throws SQLException;

    List<ClaimReviewComment> findByReviewId(UUID reviewId) throws SQLException;

    List<ClaimReviewComment> findByClaimId(UUID claimId) throws SQLException;

    List<ClaimReviewComment> findByClaimIdAndVisibility(UUID claimId, CommentVisibility visibility) throws SQLException;

    long count() throws SQLException;
}
