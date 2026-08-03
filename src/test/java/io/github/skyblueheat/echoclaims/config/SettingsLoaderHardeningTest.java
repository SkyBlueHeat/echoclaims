package io.github.skyblueheat.echoclaims.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLoaderHardeningTest {

    // ---- SQLite filename: accepted values ----

    @ParameterizedTest
    @ValueSource(strings = {
            "echoclaims.db",
            "echo-claims.db",
            "echo_claims.db",
            "claims.v1.db",
            "a.db"
    })
    void safeFilenamesAreAccepted(String filename) {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", filename)
        )));

        assertEquals(filename.strip(), result.settings().sqliteFile(),
                "safe filename should be accepted as-is: " + filename);
    }

    // ---- SQLite filename: rejected values ----

    @ParameterizedTest
    @ValueSource(strings = {
            ".",
            "..",
            "../claims.db",
            "folder/claims.db",
            "folder\\claims.db",
            "/claims.db",
            "/var/claims.db",
            "C:\\claims.db",
            "  ",
            ""
    })
    void unsafeFilenamesFallBackToDefault(String filename) {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", filename)
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile(),
                "unsafe filename should fall back: " + filename);
    }

    @Test
    void nullSqliteFileFallsBackToDefault() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of()
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile());
    }

    @Test
    void sqliteFileExceeding255CharsFallsBackToDefault() {
        String longName = "a".repeat(256) + ".db";
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", longName)
        )));

        assertEquals("echoclaims.db", result.settings().sqliteFile());
    }

    @Test
    void sqliteFileAt255CharsIsAccepted() {
        String maxName = "a".repeat(251) + ".db";
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("sqlite-file", maxName)
        )));

        assertEquals(maxName, result.settings().sqliteFile());
    }

    // ---- Locale validation ----

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
    void nullLocaleFallsBackToEnglish() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "locale", ""
        )));

        assertEquals("en", result.settings().locale());
    }

    // ---- Numeric clamping ----

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

    // ---- Other config validation ----

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
}
