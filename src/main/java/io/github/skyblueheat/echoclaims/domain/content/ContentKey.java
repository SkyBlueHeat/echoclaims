package io.github.skyblueheat.echoclaims.domain.content;

import java.util.Objects;

public record ContentKey(String providerId, String contentId) {

    public ContentKey {
        providerId = requireIdentifier(providerId, "providerId");
        contentId = requireIdentifier(contentId, "contentId");
    }

    private static String requireIdentifier(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }

    public static ContentKey parse(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Content key cannot be blank");
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon >= trimmed.length() - 1) {
            throw new IllegalArgumentException("Invalid content key format: " + value);
        }
        return new ContentKey(trimmed.substring(0, colon), trimmed.substring(colon + 1));
    }

    @Override
    public String toString() {
        return providerId + ":" + contentId;
    }
}
