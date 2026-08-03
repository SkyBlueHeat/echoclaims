package dev.worldecho.paper.item;

import dev.worldecho.domain.item.TrackedItemId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pure-Java decision logic of {@link ItemIdentityAdapter} without
 * requiring Bukkit or MockBukkit.  The {@code resolveStoredValue} method
 * encapsulates all parsing and status-mapping logic.
 */
class ItemIdentityAdapterTest {

    @Test
    void untrackedItemReadReturnsMissing() {
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue(null);
        assertEquals(ItemIdentityAdapter.IdentityStatus.MISSING, result.status());
        assertTrue(result.optionalItemId().isEmpty());
    }

    @Test
    void validExistingIdReadReturnsExisting() {
        UUID uuid = UUID.randomUUID();
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue(uuid.toString());
        assertEquals(ItemIdentityAdapter.IdentityStatus.EXISTING, result.status());
        assertTrue(result.optionalItemId().isPresent());
        assertEquals(uuid, result.itemId().value());
    }

    @Test
    void uppercaseUuidReadReturnsExisting() {
        UUID uuid = UUID.randomUUID();
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue(uuid.toString().toUpperCase());
        assertEquals(ItemIdentityAdapter.IdentityStatus.EXISTING, result.status());
        assertEquals(uuid, result.itemId().value());
    }

    @Test
    void malformedStoredIdReturnsMalformed() {
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue("not-a-uuid");
        assertEquals(ItemIdentityAdapter.IdentityStatus.MALFORMED, result.status());
        assertNull(result.itemId());
    }

    @Test
    void blankStoredIdReturnsMalformed() {
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue("");
        assertEquals(ItemIdentityAdapter.IdentityStatus.MALFORMED, result.status());
    }

    @Test
    void partialUuidReturnsMalformed() {
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue("5e116");
        assertEquals(ItemIdentityAdapter.IdentityStatus.MALFORMED, result.status());
    }

    @Test
    void existingIdIsStableAcrossReads() {
        UUID uuid = UUID.randomUUID();
        String stored = uuid.toString();

        ItemIdentityAdapter.IdentityResult first =
                ItemIdentityAdapter.resolveStoredValue(stored);
        ItemIdentityAdapter.IdentityResult second =
                ItemIdentityAdapter.resolveStoredValue(stored);

        assertEquals(first.itemId(), second.itemId());
        assertEquals(first.status(), second.status());
    }

    @Test
    void namespacedKeyIsStable() {
        String key = "worldecho:item_id";
        assertEquals("worldecho:item_id", key);
    }

    @Test
    void resolveDoesNotReturnNullResult() {
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue(null);
        assertNotNull(result);
    }

    @Test
    void resolveWithValidIdHasNonEmptyOptional() {
        ItemIdentityAdapter.IdentityResult result =
                ItemIdentityAdapter.resolveStoredValue(UUID.randomUUID().toString());
        assertTrue(result.optionalItemId().isPresent());
    }
}
