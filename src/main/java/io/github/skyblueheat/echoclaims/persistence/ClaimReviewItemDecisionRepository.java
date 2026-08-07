package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ClaimReviewItemDecision} persistence.
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads,
 * never from the server main thread.</p>
 */
public interface ClaimReviewItemDecisionRepository {

    void insert(ClaimReviewItemDecision decision) throws SQLException;

    boolean update(ClaimReviewItemDecision decision, int expectedVersion) throws SQLException;

    Optional<ClaimReviewItemDecision> findByReviewIdAndEvidenceRef(
            UUID reviewId, String evidenceItemReference) throws SQLException;

    List<ClaimReviewItemDecision> findByReviewId(UUID reviewId) throws SQLException;

    List<ClaimReviewItemDecision> findByClaimId(UUID claimId) throws SQLException;

    long count() throws SQLException;
}
