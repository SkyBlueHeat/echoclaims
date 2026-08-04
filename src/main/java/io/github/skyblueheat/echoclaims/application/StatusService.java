package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.integration.IntegrationRegistry;
import io.github.skyblueheat.echoclaims.persistence.AuditWriteQueue;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Produces the data behind {@code /echoclaims status}.
 *
 * <p>All database calls are delegated to the caller so this service can run on the
 * query executor without touching the server thread.</p>
 */
public final class StatusService {

    private final Supplier<EchoClaimsSettings> settingsSupplier;
    private final DatabaseManager databaseManager;
    private final AuditWriteQueue writeQueue;
    private final IntegrationRegistry integrations;
    private final EvidencePersistenceService evidenceService;
    private final ClaimMetrics claimMetrics;
    private final ClaimLookupService claimLookupService;
    private final String pluginVersion;
    private final long startedAt;

    public StatusService(
            Supplier<EchoClaimsSettings> settingsSupplier,
            DatabaseManager databaseManager,
            AuditWriteQueue writeQueue,
            IntegrationRegistry integrations,
            EvidencePersistenceService evidenceService,
            ClaimMetrics claimMetrics,
            ClaimLookupService claimLookupService,
            String pluginVersion,
            long startedAt
    ) {
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.writeQueue = Objects.requireNonNull(writeQueue, "writeQueue");
        this.integrations = Objects.requireNonNull(integrations, "integrations");
        this.evidenceService = evidenceService;
        this.claimMetrics = claimMetrics;
        this.claimLookupService = claimLookupService;
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.startedAt = startedAt;
    }

    /**
     * Collects status information. The caller is responsible for running this on the
     * correct thread.
     */
    public StatusReport collect() {
        EchoClaimsSettings settings = settingsSupplier.get();
        AuditWriteQueue.QueueStatus queue = writeQueue.status();
        boolean dbHealthy = databaseManager.healthy();
        int schemaVersion;
        try {
            schemaVersion = databaseManager.schemaVersion();
        } catch (Exception exception) {
            schemaVersion = -1;
        }

        long uptimeMillis = System.currentTimeMillis() - startedAt;

        EvidenceMetrics.EvidenceMetricsSnapshot evidence = evidenceService != null
                ? evidenceService.metricsSnapshot()
                : new EvidenceMetrics.EvidenceMetricsSnapshot(0, 0, 0, 0, 0, 0, 0);
        int evidencePending = evidenceService != null ? evidenceService.pendingCount() : 0;

        ClaimMetrics.ClaimMetricsSnapshot claimSnap = claimMetrics != null
                ? claimMetrics.snapshot()
                : new ClaimMetrics.ClaimMetricsSnapshot(0, 0, 0, 0, 0, 0, 0);

        long totalClaims = 0;
        long draftClaims = 0;
        long submittedClaims = 0;
        long cancelledClaims = 0;
        long totalAuditEntries = 0;
        if (claimLookupService != null) {
            try {
                totalClaims = claimLookupService.countAllClaims();
                draftClaims = claimLookupService.countClaimsByStatus(
                        io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus.DRAFT);
                submittedClaims = claimLookupService.countClaimsByStatus(
                        io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus.SUBMITTED);
                cancelledClaims = claimLookupService.countClaimsByStatus(
                        io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus.CANCELLED);
                totalAuditEntries = claimLookupService.countAuditEntries();
            } catch (Exception ignored) {
                // Database may not be ready yet
            }
        }

        return new StatusReport(
                pluginVersion,
                settings.locale(),
                dbHealthy,
                schemaVersion,
                queue.pending(),
                queue.written(),
                queue.failed(),
                queue.dropped(),
                queue.overflowDropped(),
                queue.abandoned(),
                integrations.describeProviders(),
                uptimeMillis,
                evidence.acceptedCaptures(),
                evidence.persistedSnapshots(),
                evidence.persistedIncidents(),
                evidence.duplicateIncidents(),
                evidence.failedPersistence(),
                evidence.rejectedCaptures(),
                evidencePending,
                settings.claimsEnabled(),
                totalClaims,
                draftClaims,
                submittedClaims,
                cancelledClaims,
                totalAuditEntries,
                claimSnap.claimsCreated(),
                claimSnap.claimsSubmitted(),
                claimSnap.claimsCancelled(),
                claimSnap.claimsRejected(),
                claimSnap.duplicateOpenClaims(),
                claimSnap.rateLimited(),
                claimSnap.concurrencyConflicts()
        );
    }

    public record StatusReport(
            String version,
            String locale,
            boolean databaseAvailable,
            int schemaVersion,
            int pendingWrites,
            long writtenCount,
            long failedCount,
            long droppedCount,
            long overflowDroppedCount,
            long abandonedCount,
            List<String> providers,
            long uptimeMillis,
            long evidenceAccepted,
            long evidenceSnapshots,
            long evidenceIncidents,
            long evidenceDuplicates,
            long evidenceFailed,
            long evidenceRejected,
            int evidencePending,
            boolean claimsEnabled,
            long totalClaims,
            long draftClaims,
            long submittedClaims,
            long cancelledClaims,
            long totalClaimAuditEntries,
            long claimsCreated,
            long claimsSubmitted,
            long claimsCancelled,
            long claimsRejected,
            long duplicateOpenClaims,
            long rateLimitedClaims,
            long claimConcurrencyConflicts
    ) {
    }
}
