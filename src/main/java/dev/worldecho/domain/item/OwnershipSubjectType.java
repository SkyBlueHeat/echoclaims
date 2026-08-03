package dev.worldecho.domain.item;

import java.util.Locale;
import java.util.Objects;

/**
 * Type of entity that may hold or own a tracked item in WorldEcho's narrative model.
 */
public enum OwnershipSubjectType {
    PLAYER,
    ENTITY,
    CONTAINER,
    WORLD_DROP,
    SYSTEM,
    UNKNOWN;

    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OwnershipSubjectType fromToken(String token) {
        Objects.requireNonNull(token, "token");
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (OwnershipSubjectType type : values()) {
            if (type.token().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown subject type: " + token);
    }
}
