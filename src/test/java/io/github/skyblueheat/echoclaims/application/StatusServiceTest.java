package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.config.SettingsLoadResult;
import io.github.skyblueheat.echoclaims.config.SettingsLoader;
import io.github.skyblueheat.echoclaims.config.MapConfigurationSource;
import io.github.skyblueheat.echoclaims.integration.IntegrationRegistry;
import io.github.skyblueheat.echoclaims.persistence.AuditWriteQueue;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void collectReturnsAllExpectedFields() throws Exception {
        EchoClaimsSettings settings = EchoClaimsSettings.defaults();
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("status.db"));
        dbManager.initialize();

        AuditRecordRepository repository = new io.github.skyblueheat.echoclaims.persistence.SqliteAuditRecordRepository(dbManager);
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 128, 8);
        queue.start();

        IntegrationRegistry integrations = new IntegrationRegistry((id, t) -> {});

        StatusService service = new StatusService(
                () -> settings,
                dbManager,
                queue,
                integrations,
                null,
                null,
                null,
                "0.1.0-SNAPSHOT",
                System.currentTimeMillis() - 5000
        );

        StatusService.StatusReport report = service.collect();

        assertEquals("0.1.0-SNAPSHOT", report.version());
        assertEquals("en", report.locale());
        assertTrue(report.databaseAvailable());
        assertEquals(4, report.schemaVersion());
        assertEquals(0, report.pendingWrites());
        assertEquals(0, report.writtenCount());
        assertEquals(0, report.failedCount());
        assertEquals(0, report.droppedCount());
        assertEquals(0, report.overflowDroppedCount());
        assertEquals(0, report.abandonedCount());
        assertTrue(report.providers().isEmpty());
        assertTrue(report.uptimeMillis() >= 5000);

        queue.shutdown(java.time.Duration.ofSeconds(5));
    }

    @Test
    void collectReportsDatabaseUnavailableWhenFileIsMissing() {
        EchoClaimsSettings settings = EchoClaimsSettings.defaults();
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("nonexistent/nested/missing.db"));

        AuditRecordRepository repository = new io.github.skyblueheat.echoclaims.persistence.SqliteAuditRecordRepository(dbManager);
        AuditWriteQueue queue = new AuditWriteQueue(repository, t -> {}, 128, 8);

        IntegrationRegistry integrations = new IntegrationRegistry((id, t) -> {});

        StatusService service = new StatusService(
                () -> settings,
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
}
