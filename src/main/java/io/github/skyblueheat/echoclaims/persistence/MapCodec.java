package io.github.skyblueheat.echoclaims.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Encodes and decodes {@code Map<String, Integer>} and {@code Map<String, String>}
 * to and from delimited strings for SQLite text columns.
 *
 * <p>Format: {@code key1=value1;key2=value2}. The characters {@code ;}, {@code =}, and
 * {@code \} are escaped with a backslash prefix so any key or value can be stored
 * unambiguously.</p>
 */
final class MapCodec {

    private MapCodec() {
    }

    static String encodeIntMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(escape(entry.getKey()));
            builder.append('=');
            builder.append(entry.getValue());
        }
        return builder.toString();
    }

    static Map<String, Integer> decodeIntMap(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String pair : splitPairs(encoded)) {
            int separator = findUnescapedSeparator(pair);
            if (separator < 0) {
                continue;
            }
            String key = unescape(pair.substring(0, separator));
            String value = pair.substring(separator + 1);
            try {
                map.put(key, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed entries
            }
        }
        return Map.copyOf(map);
    }

    static String encodeStringMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(escape(entry.getKey()));
            builder.append('=');
            builder.append(escape(Objects.requireNonNullElse(entry.getValue(), "")));
        }
        return builder.toString();
    }

    static Map<String, String> decodeStringMap(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : splitPairs(encoded)) {
            int separator = findUnescapedSeparator(pair);
            if (separator < 0) {
                continue;
            }
            String key = unescape(pair.substring(0, separator));
            String value = unescape(pair.substring(separator + 1));
            map.put(key, value);
        }
        return Map.copyOf(map);
    }

    private static String[] splitPairs(String encoded) {
        java.util.List<String> pairs = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (escaped) {
                current.append('\\');
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ';') {
                pairs.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        pairs.add(current.toString());
        return pairs.toArray(new String[0]);
    }

    private static int findUnescapedSeparator(String pair) {
        boolean escaped = false;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '=') {
                return i;
            }
        }
        return -1;
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == ';' || c == '=') {
                builder.append('\\');
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                builder.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
