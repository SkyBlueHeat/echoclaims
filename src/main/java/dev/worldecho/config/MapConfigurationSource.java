package dev.worldecho.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory {@link ConfigurationSource} backed by nested maps. Used by tests and as the
 * defaults source when a configuration file cannot be read.
 */
public final class MapConfigurationSource implements ConfigurationSource {

    private final Map<String, Object> root;

    public MapConfigurationSource(Map<String, Object> root) {
        this.root = new LinkedHashMap<>(Objects.requireNonNull(root, "root"));
    }

    public static MapConfigurationSource empty() {
        return new MapConfigurationSource(Map.of());
    }

    @Override
    public Optional<Object> raw(String path) {
        Object value = resolve(path);
        return value instanceof Map<?, ?> ? Optional.empty() : Optional.ofNullable(value);
    }

    @Override
    public Set<String> childKeys(String path) {
        Object value = resolve(path);
        if (value instanceof Map<?, ?> map) {
            Set<String> keys = new LinkedHashSet<>();
            map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            return Set.copyOf(keys);
        }
        return Set.of();
    }

    private Object resolve(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }
}
