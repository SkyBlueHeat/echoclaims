package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only claim lookup for player and staff commands.
 *
 * <p>All methods block on disk I/O and must be called from the query executor, never
 * from the server main thread.</p>
 */
public final class ClaimLookupService {

    private final ClaimStore claimStore;
    private final int maxRecentResults;

    public ClaimLookupService(ClaimStore claimStore, int maxRecentResults) {
        this.claimStore = Objects.requireNonNull(claimStore, "claimStore");
        this.maxRecentResults = Math.max(1, Math.min(maxRecentResults, 100));
    }

    public Optional<Claim> findClaimById(UUID id) throws SQLException {
        return claimStore.findClaimById(id);
    }

    public Optional<Claim> findClaimByPublicReference(String publicReference) throws SQLException {
        return claimStore.findClaimByPublicReference(publicReference);
    }

    public List<Claim> findClaimsByPlayer(UUID playerUuid) throws SQLException {
        return claimStore.findRecentClaimsByPlayer(playerUuid, maxRecentResults);
    }

    public List<Claim> findOpenClaimsByIncident(UUID incidentId) throws SQLException {
        return claimStore.findOpenClaimsByIncident(incidentId);
    }

    public List<ClaimAuditEntry> findAuditEntries(UUID claimId) throws SQLException {
        return claimStore.findAuditEntries(claimId);
    }

    public long countClaimsByPlayerAndStatus(UUID playerUuid, ClaimStatus status) throws SQLException {
        return claimStore.countClaimsByPlayerAndStatus(playerUuid, status);
    }

    public long countAllClaims() throws SQLException {
        return claimStore.countClaims();
    }

    public long countClaimsByStatus(ClaimStatus status) throws SQLException {
        return claimStore.countClaimsByStatus(status);
    }

    public long countAuditEntries() throws SQLException {
        return claimStore.countAuditEntries();
    }

    public int maxRecentResults() {
        return maxRecentResults;
    }
}
