package io.github.skyblueheat.echoclaims.integration.bukkit;

import io.github.skyblueheat.echoclaims.integration.ItemSerializationException;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BukkitItemSerializer} that do not require a running CraftBukkit server.
 *
 * <p>Round-trip tests with real {@link org.bukkit.inventory.ItemStack} objects require
 * CraftBukkit's runtime implementation, which is not available in unit tests. Those
 * tests are covered by runtime validation on a disposable Paper server.</p>
 *
 * <p>This test class covers: null/empty handling, corrupted payload detection,
 * unsupported format version rejection, oversize enforcement, format version
 * introspection, and configuration validation.</p>
 */
class BukkitItemSerializerTest {

    @Test
    void nullItemReturnsEmptyString() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        String encoded = serializer.serialize(null);
        assertEquals("", encoded);
    }

    @Test
    void emptyStringDeserializesToNull() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack result = serializer.deserialize("");
        assertNull(result);
    }

    @Test
    void nullStringDeserializesToNull() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack result = serializer.deserialize(null);
        assertNull(result);
    }

    @Test
    void corruptedBase64ThrowsException() {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        assertThrows(ItemSerializationException.class, () ->
                serializer.deserialize("!!!not_valid_base64!!!")
        );
    }

    @Test
    void truncatedPayloadThrowsException() {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        String truncated = java.util.Base64.getEncoder().encodeToString(new byte[]{0, 1});
        assertThrows(ItemSerializationException.class, () ->
                serializer.deserialize(truncated)
        );
    }

    @Test
    void unsupportedFormatVersionThrowsException() {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(999);
        buffer.putInt(0);
        String unsupported = java.util.Base64.getEncoder().encodeToString(buffer.array());

        assertThrows(ItemSerializationException.class, () ->
                serializer.deserialize(unsupported)
        );
    }

    @Test
    void formatVersionIsOne() {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        assertEquals(1, serializer.formatVersion());
    }

    @Test
    void maxPayloadBytesReturnsConfiguredValue() {
        BukkitItemSerializer custom = new BukkitItemSerializer(1024);
        assertEquals(1024, custom.maxPayloadBytes());
    }

    @Test
    void maxPayloadBytesEnforcesMinimum() {
        BukkitItemSerializer tiny = new BukkitItemSerializer(10);
        assertEquals(256, tiny.maxPayloadBytes());
    }

    @Test
    void peekFormatVersionReturnsMinusOneForEmpty() {
        assertEquals(-1, BukkitItemSerializer.peekFormatVersion(""));
    }

    @Test
    void peekFormatVersionReturnsMinusOneForCorrupted() {
        assertEquals(-1, BukkitItemSerializer.peekFormatVersion("!!!invalid!!!"));
    }

    @Test
    void peekFormatVersionReturnsMinusOneForTooShort() {
        String shortPayload = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2});
        assertEquals(-1, BukkitItemSerializer.peekFormatVersion(shortPayload));
    }

    @Test
    void peekFormatVersionReturnsVersionForValidHeader() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(1);
        buffer.putInt(100);
        String validHeader = java.util.Base64.getEncoder().encodeToString(buffer.array());
        assertEquals(1, BukkitItemSerializer.peekFormatVersion(validHeader));
    }

    @Test
    void peekFormatVersionReturnsCustomVersion() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(42);
        buffer.putInt(0);
        String customVersion = java.util.Base64.getEncoder().encodeToString(buffer.array());
        assertEquals(42, BukkitItemSerializer.peekFormatVersion(customVersion));
    }

    @Test
    void defaultMaxPayloadBytesIsConfigured() {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        assertTrue(serializer.maxPayloadBytes() > 0);
        assertTrue(serializer.maxPayloadBytes() >= 256);
    }
}
