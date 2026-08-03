package dev.worldecho.domain.memory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, dependency-free {@code key=value;key=value} encoding used for the diagnostic
 * text columns of a story event. Values are escaped so a custom item name can never
 * corrupt a stored record.
 */
public final class Attributes {

    private static final char PAIR_SEPARATOR = ';';
    private static final char KEY_SEPARATOR = '=';
    private static final char ESCAPE = '\\';

    private final Map<String, String> values = new LinkedHashMap<>();

    public static Attributes create() {
        return new Attributes();
    }

    private Attributes() {
    }

    public Attributes put(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            values.put(key, value);
        }
        return this;
    }

    public Attributes put(String key, int value) {
        return put(key, Integer.toString(value));
    }

    public Attributes put(String key, Object value) {
        return value == null ? this : put(key, value.toString());
    }

    public String encode() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(PAIR_SEPARATOR);
            }
            builder.append(escape(entry.getKey()))
                    .append(KEY_SEPARATOR)
                    .append(escape(entry.getValue()));
        }
        return builder.toString();
    }

    public static Map<String, String> decode(String encoded) {
        Map<String, String> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }

        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean readingValue = false;
        boolean escaped = false;

        for (int index = 0; index < encoded.length(); index++) {
            char current = encoded.charAt(index);
            StringBuilder target = readingValue ? value : key;

            if (escaped) {
                target.append(current);
                escaped = false;
            } else if (current == ESCAPE) {
                escaped = true;
            } else if (current == KEY_SEPARATOR && !readingValue) {
                readingValue = true;
            } else if (current == PAIR_SEPARATOR) {
                flush(result, key, value, readingValue);
                readingValue = false;
            } else {
                target.append(current);
            }
        }

        flush(result, key, value, readingValue);
        return result;
    }

    private static void flush(
            Map<String, String> result,
            StringBuilder key,
            StringBuilder value,
            boolean readingValue
    ) {
        if (readingValue && !key.isEmpty()) {
            result.put(key.toString(), value.toString());
        }
        key.setLength(0);
        value.setLength(0);
    }

    private static String escape(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == ESCAPE || current == PAIR_SEPARATOR || current == KEY_SEPARATOR) {
                builder.append(ESCAPE);
            }
            builder.append(current);
        }
        return builder.toString();
    }
}
