package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic persistence of a claim and its audit entries.
 *
 * <p>Implementations must insert the claim and its initial audit entry in a single
 * database transaction. Either both persist or neither persists. Status transitions
 * must similarly update the claim and insert the transition audit entry atomically,
 * using optimistic concurrency on the claim version.</p>
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads, never from
 * the server main thread.</p>
 */
public interface ClaimStore {

    /**
     * Inserts a new claim and its creation audit entry atomically.
     *
     * @throws SQLException if any insert fails; the entire operation is rolled back
     */
    void createClaim(Claim claim, ClaimAuditEntry auditEntry) throws SQLException;

    /**
     * Atomically transitions a claim's status and inserts the transition audit entry.
     *
     * <p>The update only succeeds if the stored version matches {@code expectedVersion}.
     * On success, the stored version is incremented by one and the audit entry is
     * inserted with the new version.</p>
     *
     * @param claimId the claim UUID
     * @param newStatus the new status
     * @param submittedAt new submitted timestamp, or 0 to keep existing
     * @param cancelledAt new cancelled timestamp, or 0 to keep existing
     * @param expectedVersion the version the caller expects
     * @param auditEntry the audit entry to insert
     * @return {@code true} if the transition was applied, {@code false} if version mismatch
     * @throws SQLException if the operation fails for reasons other than version mismatch
     */
    boolean transitionClaim(
            UUID claimId,
            ClaimStatus newStatus,
            long submittedAt,
            long cancelledAt,
            int expectedVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException;

    /**
     * Finds a claim by its UUID.
     *
     * @return the claim, or an empty optional if not found
     * @throws SQLException if the query fails
     */
    Optional<Claim> findClaimById(UUID id) throws SQLException;

    /**
     * Finds a claim by its public reference.
     *
     * @return the claim, or an empty optional if not found
     * @throws SQLException if the query fails
     */
    Optional<Claim> findClaimByPublicReference(String publicReference) throws SQLException;

    /**
     * Finds recent claims for a player, ordered by creation time descending.
     *
     * @throws SQLException if the query fails
     */
    List<Claim> findRecentClaimsByPlayer(UUID playerUuid, int limit) throws SQLException;

    /**
     * Finds open claims (DRAFT or SUBMITTED) for a specific incident.
     *
     * @throws SQLException if the query fails
     */
    List<Claim> findOpenClaimsByIncident(UUID incidentId) throws SQLException;

    /**
     * Finds all audit entries for a claim, ordered by recorded time ascending.
     *
     * @throws SQLException if the query fails
     */
    List<ClaimAuditEntry> findAuditEntries(UUID claimId) throws SQLException;

    /**
     * Counts claims by status for a specific player.
     *
     * @throws SQLException if the query fails
     */
    long countClaimsByPlayerAndStatus(UUID playerUuid, ClaimStatus status) throws SQLException;

    /**
     * Counts all stored claims.
     *
     * @throws SQLException if the query fails
     */
    long countClaims() throws SQLException;

    /**
     * Counts claims by status.
     *
     * @throws SQLException if the query fails
     */
    long countClaimsByStatus(ClaimStatus status) throws SQLException;

    /**
     * Counts all stored claim audit entries.
     *
     * @throws SQLException if the query fails
     */
    long countAuditEntries() throws SQLException;
}
