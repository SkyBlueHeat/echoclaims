package dev.worldecho.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Locale-aware message lookup with placeholder substitution.
 *
 * <p>The catalog is pure text handling: it returns MiniMessage source strings and leaves
 * rendering to the Paper layer. A missing key falls back to the bundled English message
 * and finally to a visible diagnostic so a translation gap is never silent.</p>
 */
public final class MessageCatalog {

    private final Map<String, String> messages;
    private final Map<String, String> fallback;
    private final String prefix;

    public MessageCatalog(Map<String, String> messages, Map<String, String> fallback) {
        this.messages = Map.copyOf(Objects.requireNonNullElse(messages, Map.of()));
        this.fallback = Map.copyOf(Objects.requireNonNullElse(fallback, Map.of()));
        this.prefix = Objects.requireNonNullElse(
                this.messages.getOrDefault("prefix", this.fallback.get("prefix")),
                ""
        );
    }

    public boolean has(String key) {
        return messages.containsKey(key) || fallback.containsKey(key);
    }

    public String raw(String key) {
        String value = messages.get(key);
        if (value == null) {
            value = fallback.get(key);
        }
        return value == null ? "<red>Missing message: " + sanitize(key) + "</red>" : value;
    }

    public String format(String key) {
        return format(key, Map.of());
    }

    /**
     * Resolves {@code key} and replaces {@code {placeholder}} tokens with sanitized values.
     */
    public String format(String key, Map<String, String> placeholders) {
        String resolved = prefix + raw(key);
        if (placeholders == null || placeholders.isEmpty()) {
            return resolved;
        }

        Map<String, String> ordered = new LinkedHashMap<>(placeholders);
        for (Map.Entry<String, String> placeholder : ordered.entrySet()) {
            resolved = resolved.replace(
                    "{" + placeholder.getKey() + "}",
                    sanitize(placeholder.getValue())
            );
        }
        return resolved;
    }

    /**
     * Removes MiniMessage control characters so runtime values such as a custom item name
     * cannot inject formatting or click actions into an admin message.
     */
    static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '<' && current != '>' && current != '\\') {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
