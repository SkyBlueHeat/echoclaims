package io.github.skyblueheat.echoclaims.config;

import io.github.skyblueheat.echoclaims.application.RefundServiceConfig;
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

    @Test
    void refundDefaultsAreSensible() {
        EchoClaimsSettings settings = EchoClaimsSettings.defaults();

        assertTrue(settings.refundsEnabled());
        assertTrue(settings.refundPlayerSelfClaim());
        assertFalse(settings.refundAutoDeliverOnLogin());
        assertEquals(100, settings.refundMaxItemsPerExecution());
        assertTrue(settings.refundRetryFailedRefunds());
    }

    @Test
    void refundSettingsAreReadFromConfiguration() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "refunds", Map.of(
                        "enabled", false,
                        "player-self-claim", false,
                        "auto-deliver-on-login", true,
                        "max-items-per-execution", 50,
                        "retry-failed-refunds", false
                )
        )));

        EchoClaimsSettings settings = result.settings();
        assertFalse(settings.refundsEnabled());
        assertFalse(settings.refundPlayerSelfClaim());
        assertTrue(settings.refundAutoDeliverOnLogin());
        assertEquals(50, settings.refundMaxItemsPerExecution());
        assertFalse(settings.refundRetryFailedRefunds());
    }

    @Test
    void refundMaxItemsClampedToMinimum() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "refunds", Map.of("max-items-per-execution", 0)
        )));

        assertEquals(1, result.settings().refundMaxItemsPerExecution());
        assertTrue(result.hasWarnings());
    }

    @Test
    void refundMaxItemsClampedToMaximum() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "refunds", Map.of("max-items-per-execution", 100_000)
        )));

        assertEquals(1_000, result.settings().refundMaxItemsPerExecution());
        assertTrue(result.hasWarnings());
    }

    @Test
    void refundServiceConfigMatchesSettings() {
        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "refunds", Map.of(
                        "enabled", true,
                        "player-self-claim", false,
                        "auto-deliver-on-login", true,
                        "max-items-per-execution", 25,
                        "retry-failed-refunds", false
                )
        )));

        EchoClaimsSettings settings = result.settings();
        RefundServiceConfig config = settings.refundServiceConfig();

        assertTrue(config.enabled());
        assertFalse(config.playerSelfClaim());
        assertTrue(config.autoDeliverOnLogin());
        assertEquals(25, config.maxItemsPerExecution());
        assertFalse(config.retryFailedRefunds());
    }
}
