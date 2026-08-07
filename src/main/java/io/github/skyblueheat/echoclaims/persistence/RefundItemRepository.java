package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItemStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link RefundItem} persistence.
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads,
 * never from the server main thread.</p>
 */
public interface RefundItemRepository {

    void insert(RefundItem item) throws SQLException;

    Optional<RefundItem> findById(UUID id) throws SQLException;

    List<RefundItem> findByRefundId(UUID refundId) throws SQLException;

    List<RefundItem> findByClaimId(UUID claimId) throws SQLException;

    List<RefundItem> findByPlayerUuid(UUID playerUuid) throws SQLException;

    List<RefundItem> findPendingByRefundId(UUID refundId) throws SQLException;

    boolean updateDeliveryProgress(
            UUID itemId,
            int deliveredQuantity,
            RefundItemStatus newStatus,
            String failureReason,
            long updatedAt,
            int expectedVersion
    ) throws SQLException;

    long countByStatus(RefundItemStatus status) throws SQLException;
}
