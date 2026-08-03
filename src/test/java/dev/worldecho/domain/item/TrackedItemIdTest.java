package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackedItemIdTest {

    @Test
    void randomProducesValidId() {
        TrackedItemId id = TrackedItemId.random();
        assertNotNull(id);
        assertNotNull(id.value());
    }

    @Test
    void parseAcceptsValidUuid() {
        UUID uuid = UUID.randomUUID();
        TrackedItemId id = TrackedItemId.parse(uuid.toString());
        assertEquals(uuid, id.value());
    }

    @Test
    void parseAcceptsUpperCaseUuid() {
        UUID uuid = UUID.randomUUID();
        TrackedItemId id = TrackedItemId.parse(uuid.toString().toUpperCase());
        assertEquals(uuid, id.value());
    }

    @Test
    void parseRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> TrackedItemId.parse(""));
        assertThrows(IllegalArgumentException.class, () -> TrackedItemId.parse("   "));
    }

    @Test
    void parseRejectsInvalidString() {
        assertThrows(IllegalArgumentException.class, () -> TrackedItemId.parse("not-a-uuid"));
    }

    @Test
    void tryParseReturnsEmptyForInvalid() {
        Optional<TrackedItemId> result = TrackedItemId.tryParse("bad");
        assertTrue(result.isEmpty());
    }

    @Test
    void tryParseReturnsEmptyForNull() {
        assertTrue(TrackedItemId.tryParse(null).isEmpty());
    }

    @Test
    void toStringIsLowercase() {
        UUID uuid = UUID.randomUUID();
        TrackedItemId id = new TrackedItemId(uuid);
        assertEquals(uuid.toString().toLowerCase(java.util.Locale.ROOT), id.toString());
    }

    @Test
    void equalityBasedOnUuid() {
        UUID uuid = UUID.randomUUID();
        TrackedItemId a = new TrackedItemId(uuid);
        TrackedItemId b = new TrackedItemId(uuid);
        TrackedItemId c = TrackedItemId.random();
        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
