package io.github.skyblueheat.echoclaims.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLoaderHardeningTest {

    @Test
    void unsafeSqliteFilePathFallsBackToDefault() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", "../../../etc/passwd")
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile());
    }

    @Test
    void sqliteFilePathWithBackslashFallsBackToDefault() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", "..\\..\\windows\\system32")
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile());
    }

    @Test
    void sqliteFilePathWithSlashFallsBackToDefault() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", "subdir/data.db")
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile());
    }

    @Test
    void blankSqliteFilePathFallsBackToDefault() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", "   ")
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile());
    }

    @Test
    void dotSqliteFilePathFallsBackToDefault() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", ".")
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile());
    }

    @Test
    void invalidLocaleFallsBackToEnglish() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "locale", "xyz"
        )));

        assertEquals("en", result.settings().locale());
    }

    @Test
    void numericLocaleFallsBackToEnglish() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "locale", 123
        )));

        assertEquals("en", result.settings().locale());
        assertTrue(result.hasWarnings());
    }

    @Test
    void extremeQueueCapacityIsClamped() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("write-queue-capacity", -1)
        )));

        assertEquals(16, result.settings().writeQueueCapacity());
        assertTrue(result.hasWarnings());
    }

    @Test
    void extremeBatchSizeIsClamped() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("write-batch-size", 0)
        )));

        assertEquals(1, result.settings().writeBatchSize());
        assertTrue(result.hasWarnings());
    }

    @Test
    void shutdownTimeoutMinimumIsEnforced() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("shutdown-timeout-seconds", -5)
        )));

        assertEquals(1, result.settings().shutdownTimeout().toSeconds());
        assertTrue(result.hasWarnings());
    }

    @Test
    void shutdownTimeoutMaximumIsEnforced() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("shutdown-timeout-seconds", Integer.MAX_VALUE)
        )));

        assertEquals(300, result.settings().shutdownTimeout().toSeconds());
        assertTrue(result.hasWarnings());
    }

    @Test
    void batchSizeLargerThanCapacityIsAllowed() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of(
                        "write-queue-capacity", 16,
                        "write-batch-size", 1000
                )
        )));

        assertEquals(16, result.settings().writeQueueCapacity());
        assertEquals(1000, result.settings().writeBatchSize());
    }

    @Test
    void malformedScoringMapIsIgnored() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "scoring", Map.of(
                        "material-scores", Map.of("diamond_sword", "not-a-number")
                )
        )));

        assertTrue(result.hasWarnings());
    }

    @Test
    void retentionDaysIsNotPresentInSettings() {
        SettingsLoadResult result = SettingsLoader.load(MapConfigurationSource.empty());

        try {
            result.settings().getClass().getDeclaredField("retentionDays");
            throw new AssertionError("retentionDays field should not exist in EchoClaimsSettings");
        } catch (NoSuchFieldException e) {
            // expected: field was removed
        }
    }

    @Test
    void booleanAsStringFallsBack() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "debug", "true"
        )));

        assertFalse(result.settings().debugLogging());
        assertTrue(result.hasWarnings());
    }

    @Test
    void nullLocaleFallsBackToEnglish() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "locale", ""
        )));

        assertEquals("en", result.settings().locale());
    }
}
