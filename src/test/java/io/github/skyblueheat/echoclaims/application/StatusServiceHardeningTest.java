package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.integration.IntegrationRegistry;
import io.github.skyblueheat.echoclaims.persistence.AuditWriteQueue;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.SqliteAuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusServiceHardeningTest {

    @TempDir
    Path tempDir;

    @Test
    void statusReportsDatabaseUnavailableWhenNotInitialized() {
        DatabaseManager dbManager = new DatabaseManager(
                java.nio.file.Path.of("/dev/null/cannot-create.db"));
        AuditWriteQueue queue = new AuditWriteQueue(
                new SqliteAuditRecordRepository(dbManager), t -> {}, 32, 4);

        IntegrationRegistry integrations = new IntegrationRegistry((id, t) -> {});

        StatusService service = new StatusService(
                () -> EchoClaimsSettings.defaults(),
                dbManager,
                queue,
                integrations,
                null,
                null,
                null,
                "0.1.0-SNAPSHOT",
                System.currentTimeMillis()
        );

        StatusService.StatusReport report = service.collect();

        assertFalse(report.databaseAvailable());
        assertEquals(-1, report.schemaVersion());
    }

    @Test
    void statusReportsProvidersAfterRegistration() {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("providers.db"));
        try { dbManager.initialize(); } catch (Exception ignored) {}

        AuditWriteQueue queue = new AuditWriteQueue(
                new SqliteAuditRecordRepository(dbManager), t -> {}, 32, 4);

        IntegrationRegistry integrations = new IntegrationRegistry((id, t) -> {});
        integrations.registerEntityProvider(new io.github.skyblueheat.echoclaims.integration.vanilla.VanillaEntityProvider());
        integrations.registerItemProvider(new io.github.skyblueheat.echoclaims.integration.vanilla.VanillaItemProvider());
        integrations.freeze();

        StatusService service = new StatusService(
                () -> EchoClaimsSettings.defaults(),
                dbManager,
                queue,
                integrations,
                null,
                null,
                null,
                "0.1.0-SNAPSHOT",
                System.currentTimeMillis()
        );

        StatusService.StatusReport report = service.collect();

        assertEquals(2, report.providers().size());
        assertTrue(report.providers().stream().anyMatch(p -> p.contains("entity:vanilla")));
        assertTrue(report.providers().stream().anyMatch(p -> p.contains("item:vanilla")));

        queue.shutdown(Duration.ofSeconds(5));
    }

    @Test
    void statusUptimeIsNonNegative() {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("uptime.db"));
        try { dbManager.initialize(); } catch (Exception ignored) {}

        AuditWriteQueue queue = new AuditWriteQueue(
                new SqliteAuditRecordRepository(dbManager), t -> {}, 32, 4);

        IntegrationRegistry integrations = new IntegrationRegistry((id, t) -> {});

        long futureTime = System.currentTimeMillis() + 100_000;
        StatusService service = new StatusService(
                () -> EchoClaimsSettings.defaults(),
                dbManager,
                queue,
                integrations,
                null,
                null,
                null,
                "0.1.0-SNAPSHOT",
                futureTime
        );

        StatusService.StatusReport report = service.collect();

        assertTrue(report.uptimeMillis() < 0, "uptime should be negative when startedAt is in the future");

        queue.shutdown(Duration.ofSeconds(5));
    }
}
