package io.github.skyblueheat.echoclaims.resources;

import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.config.SettingsLoadResult;
import io.github.skyblueheat.echoclaims.config.SettingsLoader;
import io.github.skyblueheat.echoclaims.paper.config.BukkitConfigurationSource;
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
 * defaults, every English message must have a Turkish counterpart, and the plugin.yml
 * must contain the EchoClaims identity.
 */
class BundledResourcesTest {

    @Test
    void shippedConfigMatchesTheBuiltInDefaults() {
        SettingsLoadResult shipped = SettingsLoader.load(
                new BukkitConfigurationSource(read("config.yml")));

        assertFalse(shipped.hasWarnings(), () -> "config.yml warnings: " + shipped.warnings());
        assertEquals(EchoClaimsSettings.defaults(), shipped.settings());
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
                "prefix", "no-permission", "not-ready", "usage", "unknown-subcommand",
                "status-header", "status-line")) {
            assertTrue(english.isString(key), () -> "missing message key: " + key);
        }
    }

    @Test
    void pluginYmlContainsEchoClaimsIdentity() {
        YamlConfiguration pluginYml = read("plugin.yml");

        assertEquals("EchoClaims", pluginYml.getString("name"));
        assertEquals("io.github.skyblueheat.echoclaims.paper.EchoClaimsPlugin", pluginYml.getString("main"));
        assertNotNull(pluginYml.getConfigurationSection("commands.echoclaims"));
        assertNotNull(pluginYml.getConfigurationSection("permissions.echoclaims.admin"));
        assertNotNull(pluginYml.getConfigurationSection("permissions.echoclaims.command.status"));
    }

    @Test
    void noWorldEchoBrandingInRuntimeResources() {
        String config = readText("config.yml");
        String pluginYml = readText("plugin.yml");
        String messagesEn = readText("messages_en.yml");
        String messagesTr = readText("messages_tr.yml");

        for (String content : Set.of(config, pluginYml, messagesEn, messagesTr)) {
            assertFalse(content.toLowerCase().contains("worldecho"),
                    "WorldEcho reference found in runtime resource");
        }
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

    private static String readText(String resource) {
        try (InputStream stream = BundledResourcesTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, () -> resource + " is not packaged");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new AssertionError("Could not read " + resource, exception);
        }
    }
}
