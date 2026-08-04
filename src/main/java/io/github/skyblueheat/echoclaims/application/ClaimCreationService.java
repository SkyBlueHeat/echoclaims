package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates new claims with full validation, duplicate protection, and atomic persistence.
 *
 * <p>This service is called from a worker thread. The caller is responsible for
 * capturing Bukkit data on the main thread and passing immutable values.</p>
 */
public final class ClaimCreationService {

    private final ClaimStore claimStore;
    private final ClaimReferenceGenerator referenceGenerator;
    private final ClaimMetrics metrics;

    public ClaimCreationService(
            ClaimStore claimStore,
            ClaimReferenceGenerator referenceGenerator,
            ClaimMetrics metrics
    ) {
        this.claimStore = Objects.requireNonNull(claimStore, "claimStore");
        this.referenceGenerator = Objects.requireNonNull(referenceGenerator, "referenceGenerator");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Creates and persists a new claim.
     *
     * <p>The claim is created in {@code DRAFT} status with a creation audit entry.
     * Both are persisted atomically in a single transaction.</p>
     *
     * @param incident the incident being claimed
     * @param playerUuid the claiming player's UUID
     * @param description optional player-provided description
     * @return the created claim
     * @throws SQLException if persistence fails
     */
    public Claim createClaim(Incident incident, UUID playerUuid, String description) throws SQLException {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(playerUuid, "playerUuid");

        long now = System.currentTimeMillis();
        UUID claimId = UUID.randomUUID();

        String publicReference = referenceGenerator.generate();

        Claim claim = new Claim(
                claimId,
                publicReference,
                incident.id(),
                playerUuid,
                ClaimStatus.DRAFT,
                ClaimSource.PLAYER_COMMAND,
                description,
                now,
                0L,
                0L,
                0,
                java.util.Map.of()
        );

        ClaimAuditEntry auditEntry = new ClaimAuditEntry(
                UUID.randomUUID(),
                claimId,
                ClaimActorType.PLAYER,
                playerUuid,
                ClaimAction.CREATED,
                "Claim created via player command",
                now,
                0,
                java.util.Map.of()
        );

        claimStore.createClaim(claim, auditEntry);
        metrics.recordClaimCreated();

        return claim;
    }
}
