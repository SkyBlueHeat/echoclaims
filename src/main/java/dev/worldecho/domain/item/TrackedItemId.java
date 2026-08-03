package dev.worldecho.domain.item;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, stable identifier for a WorldEcho-tracked item.
 *
 * <p>Stored as a canonical lowercase UUID string in both the SQLite database and the
 * Paper Persistent Data Container.  No Bukkit imports.
 */
public record TrackedItemId(UUID value) {

    public TrackedItemId {
        Objects.requireNonNull(value, "value");
    }

    public static TrackedItemId random() {
        return new TrackedItemId(UUID.randomUUID());
    }

    public static TrackedItemId parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("TrackedItemId cannot be blank");
        }
        try {
            return new TrackedItemId(UUID.fromString(trimmed.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid TrackedItemId: " + text, exception);
        }
    }

    public static Optional<TrackedItemId> tryParse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(text));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value.toString().toLowerCase(Locale.ROOT);
    }
}
