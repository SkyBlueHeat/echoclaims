package dev.worldecho.config;

import dev.worldecho.domain.binding.BindingDiagnostic;
import dev.worldecho.domain.binding.BindingDiagnostic.Severity;
import dev.worldecho.domain.binding.BindingRegistry;
import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.binding.ContentBinding;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Validates untrusted {@code bindings.yml} data into an immutable {@link BindingRegistry}.
 *
 * <p>The loader consumes a provider-neutral {@link ConfigurationSource} so it can be tested
 * without Bukkit. Invalid entries produce diagnostics and are skipped; valid entries in
 * the same file are still loaded. A completely unparseable file (missing version, wrong
 * root structure) produces an error and an empty registry.</p>
 */
public final class BindingLoader {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final Set<String> KNOWN_BINDING_FIELDS = Set.of(
            "roles", "capabilities", "faction", "rank", "superior", "tags"
    );

    private BindingLoader() {
    }

    /**
     * Loads and validates bindings from the given source.
     *
     * @return a load result containing the registry and fatal-error flag
     */
    public static BindingLoadResult load(ConfigurationSource source) {
        Objects.requireNonNull(source, "source");
        List<BindingDiagnostic> diagnostics = new ArrayList<>();

        Optional<Object> versionRaw = source.raw("version");
        if (versionRaw.isEmpty()) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, "version",
                    "Missing schema version; expected " + SUPPORTED_SCHEMA_VERSION
            ));
            return new BindingLoadResult(BindingRegistry.empty(), diagnostics, true);
        }

        if (!(versionRaw.get() instanceof Number number)) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, "version",
                    "Schema version must be a number; got " + describeType(versionRaw.get())
            ));
            return new BindingLoadResult(BindingRegistry.empty(), diagnostics, true);
        }

        int version = number.intValue();
        if (version != SUPPORTED_SCHEMA_VERSION) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, "version",
                    "Unsupported schema version " + version + "; expected " + SUPPORTED_SCHEMA_VERSION
            ));
            return new BindingLoadResult(BindingRegistry.empty(), diagnostics, true);
        }

        Set<String> bindingsKeys = source.childKeys("bindings");
        if (bindingsKeys.isEmpty()) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.WARNING, "bindings",
                    "No 'bindings' section found"
            ));
            return new BindingLoadResult(
                    new BindingRegistry(Map.of(), Map.of(), List.copyOf(diagnostics), version),
                    diagnostics, false
            );
        }

        Map<ContentKey, ContentBinding> entityBindings = new LinkedHashMap<>();
        Map<ContentKey, ContentBinding> itemBindings = new LinkedHashMap<>();

        loadSection(source, diagnostics, "bindings.entities", BindingType.ENTITY, entityBindings);
        loadSection(source, diagnostics, "bindings.items", BindingType.ITEM, itemBindings);

        BindingRegistry registry = new BindingRegistry(
                entityBindings, itemBindings, List.copyOf(diagnostics), version
        );
        return new BindingLoadResult(registry, diagnostics, false);
    }

    private static void loadSection(
            ConfigurationSource source,
            List<BindingDiagnostic> diagnostics,
            String sectionPath,
            BindingType type,
            Map<ContentKey, ContentBinding> target
    ) {
        Set<String> keys = source.childKeys(sectionPath);
        if (keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            String entryPath = sectionPath + "." + key;
            loadEntry(source, diagnostics, entryPath, key, type, target);
        }
    }

    private static void loadEntry(
            ConfigurationSource source,
            List<BindingDiagnostic> diagnostics,
            String entryPath,
            String rawKey,
            BindingType type,
            Map<ContentKey, ContentBinding> target
    ) {
        Set<String> childKeys = source.childKeys(entryPath);
        if (childKeys.isEmpty()) {
            Object raw = source.raw(entryPath).orElse(null);
            if (raw != null && !(raw instanceof Map)) {
                diagnostics.add(new BindingDiagnostic(
                        Severity.ERROR, entryPath,
                        "Binding entry must be a section, got " + describeType(raw)
                ));
                return;
            }
        }

        ContentKey contentKey;
        try {
            contentKey = ContentKey.parse(rawKey);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, entryPath,
                    "Malformed content key '" + rawKey + "': " + exception.getMessage()
            ));
            return;
        }

        Set<SemanticRole> roles = parseEnumList(
                source, diagnostics, entryPath + ".roles", SemanticRole.class
        );
        Set<Capability> capabilities = parseEnumList(
                source, diagnostics, entryPath + ".capabilities", Capability.class
        );

        String faction = parseOptionalString(source, diagnostics, entryPath + ".faction");
        String rank = parseOptionalString(source, diagnostics, entryPath + ".rank");

        ContentKey superior = parseSuperior(source, diagnostics, entryPath + ".superior");

        Set<String> tags = parseTags(source, diagnostics, entryPath + ".tags");

        checkUnknownFields(source, diagnostics, entryPath, childKeys);

        ContentBinding binding = new ContentBinding(
                contentKey, type, roles, capabilities, faction, rank, superior, tags
        );

        if (target.containsKey(contentKey)) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.WARNING, entryPath,
                    "Duplicate " + type.name().toLowerCase() + " binding for '" + contentKey
                            + "'; the last definition wins"
            ));
        }
        target.put(contentKey, binding);
    }

    private static <E extends Enum<E>> Set<E> parseEnumList(
            ConfigurationSource source,
            List<BindingDiagnostic> diagnostics,
            String path,
            Class<E> enumClass
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return Set.of();
        }

        if (!(raw.get() instanceof List<?> list)) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, path,
                    "Expected a list, got " + describeType(raw.get())
            ));
            return Set.of();
        }

        Set<E> values = new LinkedHashSet<>();
        int index = 0;
        for (Object element : list) {
            String elementPath = path + "[" + index + "]";
            if (!(element instanceof String token)) {
                diagnostics.add(new BindingDiagnostic(
                        Severity.ERROR, elementPath,
                        "Expected a string, got " + describeType(element)
                ));
                index++;
                continue;
            }

            String normalized = token.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                values.add(Enum.valueOf(enumClass, normalized));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(new BindingDiagnostic(
                        Severity.WARNING, elementPath,
                        "Unknown " + enumClass.getSimpleName().toLowerCase() + " '" + token + "'"
                ));
            }
            index++;
        }
        return values;
    }

    private static String parseOptionalString(
            ConfigurationSource source,
            List<BindingDiagnostic> diagnostics,
            String path
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return null;
        }

        if (!(raw.get() instanceof String value)) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, path,
                    "Expected a string, got " + describeType(raw.get())
            ));
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ContentKey parseSuperior(
            ConfigurationSource source,
            List<BindingDiagnostic> diagnostics,
            String path
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return null;
        }

        if (!(raw.get() instanceof String value)) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, path,
                    "Expected a content key string, got " + describeType(raw.get())
            ));
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            return ContentKey.parse(trimmed);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, path,
                    "Invalid superior content key '" + trimmed + "': " + exception.getMessage()
            ));
            return null;
        }
    }

    private static Set<String> parseTags(
            ConfigurationSource source,
            List<BindingDiagnostic> diagnostics,
            String path
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return Set.of();
        }

        if (!(raw.get() instanceof List<?> list)) {
            diagnostics.add(new BindingDiagnostic(
                    Severity.ERROR, path,
                    "Expected a list, got " + describeType(raw.get())
            ));
            return Set.of();
        }

        Set<String> tags = new LinkedHashSet<>();
        for (Object element : list) {
            if (element instanceof String tag) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            } else {
                diagnostics.add(new BindingDiagnostic(
                        Severity.WARNING, path,
                        "Non-string tag ignored: " + describeType(element)
                ));
            }
        }
        return tags;
    }

    private static void checkUnknownFields(
            ConfigurationSource source,
            List<BindingDiagnostic> diagnostics,
            String entryPath,
            Set<String> childKeys
    ) {
        for (String field : childKeys) {
            if (!KNOWN_BINDING_FIELDS.contains(field)) {
                diagnostics.add(new BindingDiagnostic(
                        Severity.WARNING, entryPath + "." + field,
                        "Unknown field '" + field + "'"
                ));
            }
        }
    }

    private static String describeType(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
