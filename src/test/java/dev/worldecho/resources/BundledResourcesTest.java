package dev.worldecho.resources;

import dev.worldecho.config.BindingLoadResult;
import dev.worldecho.config.BindingLoader;
import dev.worldecho.config.SettingsLoadResult;
import dev.worldecho.config.SettingsLoader;
import dev.worldecho.config.WorldEchoSettings;
import dev.worldecho.paper.config.BukkitConfigurationSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped resources: the documented config must produce exactly the built-in
 * defaults, every English message must have a Turkish counterpart, and the bundled
 * bindings.yml must be parseable with a supported schema version and no errors.
 */
class BundledResourcesTest {

    @Test
    void shippedConfigMatchesTheBuiltInDefaults() {
        SettingsLoadResult shipped = SettingsLoader.load(
                new BukkitConfigurationSource(read("config.yml")));

        assertFalse(shipped.hasWarnings(), () -> "config.yml warnings: " + shipped.warnings());
        assertEquals(WorldEchoSettings.defaults(), shipped.settings());
    }

    @Test
    void everyEnglishMessageHasATurkishTranslation() {
        Set<String> english = new TreeSet<>(read("messages_en.yml").getKeys(true));
        Set<String> turkish = new TreeSet<>(read("messages_tr.yml").getKeys(true));

        assertEquals(english, turkish);
        assertFalse(english.isEmpty());
    }

    @Test
    void messagesUsedByTheCommandExist() {
        YamlConfiguration english = read("messages_en.yml");

        for (String key : Set.of(
                "prefix", "no-permission", "players-only", "usage", "unknown-subcommand",
                "status-header", "status-line", "status-failed",
                "recent-loading", "recent-header", "recent-empty", "recent-line",
                "recent-failed", "recent-invalid-count",
                "inspect-usage", "inspect-header", "inspect-no-item", "inspect-no-entity",
                "reload-success", "reload-warning", "reload-failed",
                "eligibility-usage", "eligibility-profiles-header", "eligibility-profile-line",
                "eligibility-check-usage", "eligibility-all-usage",
                "eligibility-invalid-target", "eligibility-invalid-key",
                "eligibility-result-line", "eligibility-all-header", "eligibility-all-limit",
                "item-usage", "item-track-success", "item-already-tracked", "item-malformed-id",
                "item-missing", "item-not-tracked", "item-persistence-missing",
                "item-reconciliation-success", "item-unsupported", "item-track-failed",
                "item-id-line", "item-tracked-status", "item-content-key", "item-material",
                "item-current-owner", "item-no-owner", "item-ownership-sequence",
                "item-history-count", "item-identity-warning",
                "item-history-header", "item-history-empty", "item-history-entry",
                "item-owner-header", "item-assign-success", "item-assign-invalid-uuid",
                "item-assign-invalid-subject", "item-assign-not-tracked", "item-assign-failed",
                "item-console-requires-id", "item-player-held-required",
                "item-database-failed", "item-invalid-id", "item-history-limit-clamped",
                "item-reconcile-usage", "item-reconcile-started", "item-reconcile-completed",
                "item-player-not-found", "item-reconcile-console-usage",
                "item-policy-header", "item-policy-mode", "item-policy-existing",
                "item-policy-reasons", "item-policy-confidence", "item-policy-lot-fingerprint",
                "item-policy-no-item", "item-policy-unique", "item-policy-lot",
                "item-policy-yes", "item-policy-no", "item-policy-none",
                "item-track-diagnostic", "item-malformed-identity", "item-duplicate-identity",
                "item-persistence-failure", "item-automatic-tracking-disabled")) {
            assertTrue(english.isString(key), () -> "missing message key: " + key);
        }
    }

    @Test
    void bundledBindingsYmlIsParseableWithSupportedSchema() {
        BindingLoadResult result = BindingLoader.load(
                new BukkitConfigurationSource(read("bindings.yml")));

        assertFalse(result.fatalError(), () -> "bindings.yml fatal error: " + result.diagnostics());
        assertEquals(BindingLoader.SUPPORTED_SCHEMA_VERSION, result.registry().schemaVersion());
        assertFalse(result.hasErrors(), () -> "bindings.yml errors: " + result.diagnostics());
    }

    @Test
    void bundledBindingsYmlHasValidExampleTokens() {
        BindingLoadResult result = BindingLoader.load(
                new BukkitConfigurationSource(read("bindings.yml")));

        assertTrue(result.registry().entityBindingCount() >= 2, "expected at least two entity bindings");
        assertTrue(result.registry().itemBindingCount() >= 2, "expected at least two item bindings");

        assertFalse(result.registry().findEntityBinding(
                new dev.worldecho.domain.content.ContentKey("mythicmobs", "goblin_soldier")).isEmpty(),
                "expected mythicmobs:goblin_soldier entity binding");
        assertFalse(result.registry().findItemBinding(
                new dev.worldecho.domain.content.ContentKey("oraxen", "flame_sword")).isEmpty(),
                "expected oraxen:flame_sword item binding");
    }

    @Test
    void bundledBindingsProduceDeterministicEligibilityResults() {
        BindingLoadResult result = BindingLoader.load(
                new BukkitConfigurationSource(read("bindings.yml")));
        dev.worldecho.domain.scenario.EligibilityEvaluator evaluator =
                new dev.worldecho.domain.scenario.EligibilityEvaluator(
                        dev.worldecho.domain.scenario.EligibilityCatalog.builtin());

        dev.worldecho.domain.content.ContentKey zombie =
                new dev.worldecho.domain.content.ContentKey("minecraft", "zombie");
        dev.worldecho.domain.binding.ContentBinding zombieBinding =
                result.registry().findEntityBinding(zombie).orElseThrow();
        dev.worldecho.domain.scenario.EligibilityResult r1 =
                evaluator.evaluate(zombieBinding, "combat-story-actor");
        dev.worldecho.domain.scenario.EligibilityResult r2 =
                evaluator.evaluate(zombieBinding, "combat-story-actor");

        assertEquals(r1, r2, "eligibility results should be deterministic");
        assertTrue(r1.eligible(), "minecraft:zombie should be eligible for combat-story-actor");
    }

    private static YamlConfiguration read(String resource) {
        try (InputStream stream = BundledResourcesTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, () -> resource + " is not packaged");

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (Exception exception) {
            throw new AssertionError("Could not read " + resource, exception);
        }
    }
}
