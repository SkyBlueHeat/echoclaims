package dev.worldecho.config;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only view over untrusted configuration data.
 *
 * <p>Values are returned raw so {@link SettingsLoader} can validate types itself instead
 * of relying on a silently coercing configuration API.</p>
 */
public interface ConfigurationSource {

    Optional<Object> raw(String path);

    /**
     * Direct child keys of a section, or an empty set when the path is missing or is not
     * a section.
     */
    Set<String> childKeys(String path);
}
