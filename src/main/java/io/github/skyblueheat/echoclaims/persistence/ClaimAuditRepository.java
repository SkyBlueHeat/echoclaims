package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ClaimAuditEntry} persistence.
 *
 * <p>Audit entries are append-only: they are never modified or deleted.
 * Implementations are called exclusively from EchoClaims worker threads.</p>
 */
public interface ClaimAuditRepository {

    /**
     * Inserts an audit entry.
     *
     * @throws SQLException if the insert fails
     */
    void insert(ClaimAuditEntry entry) throws SQLException;

    /**
     * Finds all audit entries for a claim, ordered by recorded time ascending.
     *
     * @param claimId the claim UUID
     * @throws SQLException if the query fails
     */
    List<ClaimAuditEntry> findByClaimId(UUID claimId) throws SQLException;

    /**
     * Counts all stored audit entries.
     *
     * @throws SQLException if the query fails
     */
    long count() throws SQLException;
}
