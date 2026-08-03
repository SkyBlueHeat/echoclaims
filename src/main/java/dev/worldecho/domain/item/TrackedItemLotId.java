package dev.worldecho.domain.item;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, stable identifier for a WorldEcho-tracked item lot.
 *
 * <p>A lot represents a quantity of interchangeable fungible items with shared
 * provenance and ownership.  Stored as a canonical lowercase UUID string.
 */
public record TrackedItemLotId(UUID value) {

    public TrackedItemLotId {
        Objects.requireNonNull(value, "value");
    }

    public static TrackedItemLotId random() {
        return new TrackedItemLotId(UUID.randomUUID());
    }

    public static TrackedItemLotId parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("TrackedItemLotId cannot be blank");
        }
        try {
            return new TrackedItemLotId(UUID.fromString(trimmed.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid TrackedItemLotId: " + text, exception);
        }
    }

    public static Optional<TrackedItemLotId> tryParse(String text) {
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
