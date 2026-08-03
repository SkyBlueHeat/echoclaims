package dev.worldecho.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLoaderTest {

    @Test
    void emptyConfigurationProducesUsableDefaults() {
        SettingsLoadResult result = SettingsLoader.load(MapConfigurationSource.empty());

        assertFalse(result.hasWarnings());
        WorldEchoSettings settings = result.settings();
        assertEquals("en", settings.locale());
        assertTrue(settings.playerDeathCaptureEnabled());
        assertEquals(25, settings.minimumItemScore());
        assertEquals("worldecho.db", settings.sqliteFile());
        assertEquals(10, settings.recentDefaultCount());
        assertEquals(50, settings.recentMaximumCount());
    }

    @Test
    void invalidTypesFallBackToDefaultsAndWarn() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "capture", Map.of("player-deaths", Map.of(
                        "enabled", "yes",
                        "minimum-item-score", "high"
                ))
        )));

        assertTrue(result.settings().playerDeathCaptureEnabled());
        assertEquals(25, result.settings().minimumItemScore());
        assertEquals(2, result.warnings().size());
    }

    @Test
    void outOfRangeNumbersAreClampedAndWarn() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "persistence", Map.of("shutdown-timeout-seconds", 100_000),
                "commands", Map.of("recent-maximum-count", 0)
        )));

        assertEquals(300, result.settings().shutdownTimeout().toSeconds());
        assertEquals(10, result.settings().recentMaximumCount());
        assertTrue(result.hasWarnings());
    }

    @Test
    void ignoredWorldsAreReadCaseInsensitively() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "capture", Map.of("player-deaths", Map.of(
                        "ignored-worlds", List.of("Creative_Plots", 42)
                ))
        )));

        assertTrue(result.settings().isWorldIgnored("creative_plots"));
        assertFalse(result.settings().isWorldIgnored("world"));
        assertEquals(1, result.warnings().size());
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
}
