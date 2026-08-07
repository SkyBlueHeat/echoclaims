package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.refund.RefundAuditEntry;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link RefundAuditEntry} persistence.
 *
 * <p>Audit entries are append-only: they are never modified or deleted.
 * Implementations are called exclusively from EchoClaims worker threads,
 * never from the server main thread.</p>
 */
public interface RefundAuditRepository {

    void insert(RefundAuditEntry entry) throws SQLException;

    List<RefundAuditEntry> findByRefundId(UUID refundId) throws SQLException;

    List<RefundAuditEntry> findByClaimId(UUID claimId) throws SQLException;

    List<RefundAuditEntry> findByPlayerUuid(UUID playerUuid, int limit) throws SQLException;
}
