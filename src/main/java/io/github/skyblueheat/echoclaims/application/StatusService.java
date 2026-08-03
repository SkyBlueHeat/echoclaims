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
    private final String pluginVersion;
    private final long startedAt;

    public StatusService(
            Supplier<EchoClaimsSettings> settingsSupplier,
            DatabaseManager databaseManager,
            AuditWriteQueue writeQueue,
            IntegrationRegistry integrations,
            String pluginVersion,
            long startedAt
    ) {
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.writeQueue = Objects.requireNonNull(writeQueue, "writeQueue");
        this.integrations = Objects.requireNonNull(integrations, "integrations");
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
                uptimeMillis
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
            long uptimeMillis
    ) {
    }
}
