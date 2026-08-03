package io.github.skyblueheat.echoclaims.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLoaderTest {

    @Test
    void emptyConfigurationProducesUsableDefaults() {
        SettingsLoadResult result = SettingsLoader.load(MapConfigurationSource.empty());

        assertFalse(result.hasWarnings());
        EchoClaimsSettings settings = result.settings();
        assertEquals("en", settings.locale());
        assertEquals("echoclaims.db", settings.sqliteFile());
        assertEquals(2000, settings.writeQueueCapacity());
        assertEquals(64, settings.writeBatchSize());
        assertFalse(settings.debugLogging());
    }

    @Test
    void invalidTypesFallBackToDefaultsAndWarn() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "debug", "yes"
        )));

        assertFalse(result.settings().debugLogging());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void outOfRangeNumbersAreClampedAndWarn() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("shutdown-timeout-seconds", 100_000)
        )));

        assertEquals(300, result.settings().shutdownTimeout().toSeconds());
        assertTrue(result.hasWarnings());
    }

    @Test
    void scoringWeightsComeFromConfiguration() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "scoring", Map.of(
                        "material-scores", Map.of("elytra", 999),
                        "custom-name-bonus", 42
                )
        )));

        assertEquals(999, result.settings().itemScoreWeights().materialScore("elytra"));
        assertEquals(42, result.settings().itemScoreWeights().customNameBonus());
    }

    @Test
    void turkishLocaleIsAccepted() {
        SettingsLoadResult result =
                SettingsLoader.load(new MapConfigurationSource(Map.of("locale", "TR")));

        assertEquals("tr", result.settings().locale());
    }

    @Test
    void messagePrefixIsReadFromConfiguration() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "message-prefix", "<gray>[Test]</gray> "
        )));

        assertEquals("<gray>[Test]</gray> ", result.settings().messagePrefix());
    }
}
