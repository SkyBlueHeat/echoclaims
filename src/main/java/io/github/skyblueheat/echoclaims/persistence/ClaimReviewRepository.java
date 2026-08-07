package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ClaimReview} persistence.
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads,
 * never from the server main thread.</p>
 */
public interface ClaimReviewRepository {

    void insert(ClaimReview review) throws SQLException;

    Optional<ClaimReview> findById(UUID id) throws SQLException;

    Optional<ClaimReview> findByClaimId(UUID claimId) throws SQLException;

    boolean updateReviewer(UUID reviewId, UUID newReviewerUuid, int expectedVersion) throws SQLException;

    boolean finalizeReview(
            UUID reviewId,
            io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome finalOutcome,
            String finalSummary,
            long finalizedAt,
            int expectedVersion
    ) throws SQLException;

    List<ClaimReview> findByReviewer(UUID reviewerUuid, int limit) throws SQLException;

    long countByState(io.github.skyblueheat.echoclaims.domain.review.ReviewState state) throws SQLException;

    long count() throws SQLException;
}
