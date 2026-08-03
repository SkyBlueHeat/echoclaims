package io.github.skyblueheat.echoclaims.paper.message;

import io.github.skyblueheat.echoclaims.config.MessageCatalog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Loads localized messages from the plugin data folder and renders them with MiniMessage.
 *
 * <p>The bundled English file is always used as the fallback catalog, so a partially
 * translated or damaged locale file still produces readable output.</p>
 */
public final class PaperMessageService {

    public static final String DEFAULT_LOCALE = "en";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final MessageCatalog catalog;
    private final String locale;

    private PaperMessageService(MessageCatalog catalog, String locale) {
        this.catalog = catalog;
        this.locale = locale;
    }

    public static PaperMessageService load(Plugin plugin, String locale) {
        Objects.requireNonNull(plugin, "plugin");
        String resolved = locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;

        Map<String, String> fallback = readResource(plugin, fileName(DEFAULT_LOCALE));
        Map<String, String> primary = DEFAULT_LOCALE.equals(resolved)
                ? fallback
                : readLocale(plugin, resolved, fallback);

        return new PaperMessageService(new MessageCatalog(primary, fallback), resolved);
    }

    public String locale() {
        return locale;
    }

    public Component component(String key) {
        return MINI_MESSAGE.deserialize(catalog.format(key));
    }

    public Component component(String key, Map<String, String> placeholders) {
        return MINI_MESSAGE.deserialize(catalog.format(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(component(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public String raw(CommandSender sender, String key) {
        return catalog.format(key);
    }

    private static Map<String, String> readLocale(
            Plugin plugin,
            String locale,
            Map<String, String> fallback
    ) {
        String fileName = fileName(locale);
        Path destination = plugin.getDataFolder().toPath().resolve(fileName);

        if (!Files.exists(destination) && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }

        if (!Files.exists(destination)) {
            plugin.getLogger().warning(
                    "Locale file " + fileName + " is missing; using " + DEFAULT_LOCALE);
            return fallback;
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(destination, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not read " + fileName + "; using " + DEFAULT_LOCALE,
                    exception
            );
            return fallback;
        }

        return flatten(configuration);
    }

    private static Map<String, String> readResource(Plugin plugin, String fileName) {
        InputStream stream = plugin.getResource(fileName);
        if (stream == null) {
            return Map.of();
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(
                    Level.WARNING, "Bundled " + fileName + " could not be read", exception);
            return Map.of();
        }

        return flatten(configuration);
    }

    private static Map<String, String> flatten(YamlConfiguration configuration) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : configuration.getKeys(true)) {
            Object value = configuration.get(key);
            if (value instanceof String text) {
                values.put(key, text);
            }
        }
        return values;
    }

    private static String fileName(String locale) {
        return "messages_" + locale + ".yml";
    }
}
