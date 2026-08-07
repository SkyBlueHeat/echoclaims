package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Refund} persistence.
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads,
 * never from the server main thread.</p>
 */
public interface RefundRepository {

    void insert(Refund refund) throws SQLException;

    Optional<Refund> findById(UUID id) throws SQLException;

    Optional<Refund> findByClaimId(UUID claimId) throws SQLException;

    Optional<Refund> findByReviewId(UUID reviewId) throws SQLException;

    boolean updateStatus(UUID refundId, RefundStatus newStatus, long completedAt, int expectedVersion) throws SQLException;

    List<Refund> findByPlayerUuid(UUID playerUuid, int limit) throws SQLException;

    List<Refund> findByPlayerUuidAndStatus(UUID playerUuid, RefundStatus status, int limit) throws SQLException;

    List<Refund> findByStatus(RefundStatus status, int limit) throws SQLException;

    long countByStatus(RefundStatus status) throws SQLException;

    long count() throws SQLException;
}
