package io.github.skyblueheat.echoclaims.paper.config;

import io.github.skyblueheat.echoclaims.config.ConfigurationSource;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Adapts a Bukkit configuration section to the provider-neutral {@link ConfigurationSource}
 * used by the validating loader.
 */
public final class BukkitConfigurationSource implements ConfigurationSource {

    private final ConfigurationSection section;

    public BukkitConfigurationSource(ConfigurationSection section) {
        this.section = Objects.requireNonNull(section, "section");
    }

    @Override
    public Optional<Object> raw(String path) {
        Object value = section.get(path);
        return value instanceof ConfigurationSection ? Optional.empty() : Optional.ofNullable(value);
    }

    @Override
    public Set<String> childKeys(String path) {
        ConfigurationSection child = section.getConfigurationSection(path);
        return child == null ? Set.of() : Set.copyOf(child.getKeys(false));
    }
}
