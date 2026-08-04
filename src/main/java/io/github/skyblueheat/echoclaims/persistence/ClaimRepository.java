package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Claim} persistence.
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads, never from
 * the server main thread. The public reference uniqueness constraint provides the final
 * barrier against duplicate claim references.</p>
 */
public interface ClaimRepository {

    /**
     * Inserts a new claim.
     *
     * @throws SQLException if the insert fails, including duplicate public reference
     */
    void insert(Claim claim) throws SQLException;

    /**
     * Updates a claim's status and related fields with optimistic concurrency.
     *
     * <p>The update only succeeds if the stored version matches {@code expectedVersion}.
     * On success, the stored version is incremented by one.</p>
     *
     * @param claimId the claim UUID
     * @param newStatus the new status
     * @param submittedAt new submitted timestamp, or 0 to keep existing
     * @param cancelledAt new cancelled timestamp, or 0 to keep existing
     * @param expectedVersion the version the caller expects to be currently stored
     * @return {@code true} if the update was applied, {@code false} if the version did not match
     * @throws SQLException if the update fails for reasons other than version mismatch
     */
    boolean updateStatus(UUID claimId, ClaimStatus newStatus, long submittedAt, long cancelledAt, int expectedVersion) throws SQLException;

    /**
     * Finds a claim by its UUID.
     *
     * @return the claim, or an empty optional if not found
     * @throws SQLException if the query fails
     */
    Optional<Claim> findById(UUID id) throws SQLException;

    /**
     * Finds a claim by its public reference (case-insensitive).
     *
     * @return the claim, or an empty optional if not found
     * @throws SQLException if the query fails
     */
    Optional<Claim> findByPublicReference(String publicReference) throws SQLException;

    /**
     * Finds recent claims for a player, ordered by creation time descending.
     *
     * @param playerUuid the player UUID
     * @param limit maximum number of results
     * @throws SQLException if the query fails
     */
    List<Claim> findRecentByPlayer(UUID playerUuid, int limit) throws SQLException;

    /**
     * Finds open claims (DRAFT or SUBMITTED) for a specific incident.
     *
     * @param incidentId the incident UUID
     * @return list of open claims linked to that incident
     * @throws SQLException if the query fails
     */
    List<Claim> findOpenByIncident(UUID incidentId) throws SQLException;

    /**
     * Counts claims by status for a specific player.
     *
     * @throws SQLException if the query fails
     */
    long countByPlayerAndStatus(UUID playerUuid, ClaimStatus status) throws SQLException;

    /**
     * Counts all stored claims.
     *
     * @throws SQLException if the query fails
     */
    long count() throws SQLException;

    /**
     * Counts claims by status.
     *
     * @throws SQLException if the query fails
     */
    long countByStatus(ClaimStatus status) throws SQLException;
}
