package io.github.skyblueheat.echoclaims.integration.bukkit;

import io.github.skyblueheat.echoclaims.integration.ItemSerializationException;
import io.github.skyblueheat.echoclaims.integration.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Objects;

/**
 * Default {@link ItemSerializer} backed by Paper's {@code ItemStack.serializeAsBytes()}
 * and {@code ItemStack.deserializeBytes()} APIs.
 *
 * <p>The serialized format is:
 * <pre>
 *   [4 bytes: format version (int, big-endian)]
 *   [N bytes: ItemStack.serializeAsBytes() output]
 * </pre>
 * The entire byte array is Base64-encoded for storage as a text field. An empty string
 * represents a null or air item.</p>
 *
 * <p>This class does not use Java native {@code ObjectOutputStream} serialization and does
 * not execute arbitrary classes during deserialization. Paper's byte serialization uses
 * NBT encoding, which is data-driven rather than class-driven.</p>
 *
 * <p>Compatibility assumption: the format version is owned by EchoClaims. If Paper's
 * internal byte format changes between Minecraft versions, the format version allows the
 * implementation to reject data serialized by an incompatible version rather than
 * silently producing corrupted items.</p>
 */
public final class BukkitItemSerializer implements ItemSerializer {

    private static final int FORMAT_VERSION = 1;
    private static final int VERSION_HEADER_SIZE = 4;

    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 65_536;

    private final int maxPayloadBytes;

    public BukkitItemSerializer() {
        this(DEFAULT_MAX_PAYLOAD_BYTES);
    }

    public BukkitItemSerializer(int maxPayloadBytes) {
        this.maxPayloadBytes = Math.max(256, maxPayloadBytes);
    }

    @Override
    public String serialize(ItemStack itemStack) throws ItemSerializationException {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "";
        }

        byte[] itemBytes;
        try {
            itemBytes = itemStack.serializeAsBytes();
        } catch (RuntimeException exception) {
            throw new ItemSerializationException(
                    "Failed to serialize item: " + itemStack.getType(), exception);
        }

        if (itemBytes == null || itemBytes.length == 0) {
            return "";
        }

        int totalSize = VERSION_HEADER_SIZE + itemBytes.length;
        if (totalSize > maxPayloadBytes) {
            throw new ItemSerializationException(
                    "Serialized item payload exceeds maximum: " + totalSize
                            + " > " + maxPayloadBytes + " bytes (material="
                            + itemStack.getType().getKey().asString() + ")");
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalSize)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(FORMAT_VERSION);
        buffer.put(itemBytes);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    @Override
    public ItemStack deserialize(String data) throws ItemSerializationException {
        if (data == null || data.isBlank()) {
            return null;
        }

        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(data.strip());
        } catch (IllegalArgumentException exception) {
            throw new ItemSerializationException("Corrupted payload: invalid Base64 encoding", exception);
        }

        if (raw.length < VERSION_HEADER_SIZE) {
            throw new ItemSerializationException(
                    "Corrupted payload: too short (" + raw.length + " bytes, minimum "
                            + VERSION_HEADER_SIZE + ")");
        }

        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        int version = buffer.getInt();
        if (version != FORMAT_VERSION) {
            throw new ItemSerializationException(
                    "Unsupported format version: " + version + " (expected " + FORMAT_VERSION + ")");
        }

        byte[] itemBytes = new byte[raw.length - VERSION_HEADER_SIZE];
        buffer.get(itemBytes);

        if (itemBytes.length == 0) {
            return null;
        }

        try {
            return ItemStack.deserializeBytes(itemBytes);
        } catch (RuntimeException exception) {
            throw new ItemSerializationException(
                    "Failed to deserialize item data", exception);
        }
    }

    @Override
    public int formatVersion() {
        return FORMAT_VERSION;
    }

    @Override
    public int maxPayloadBytes() {
        return maxPayloadBytes;
    }

    /**
     * Extracts the format version from a serialized string without fully deserializing.
     *
     * @return the format version, or {@code -1} if the data is empty or corrupted
     */
    public static int peekFormatVersion(String data) {
        Objects.requireNonNull(data, "data");
        if (data.isBlank()) {
            return -1;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(data.strip());
            if (raw.length < VERSION_HEADER_SIZE) {
                return -1;
            }
            return ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN).getInt();
        } catch (IllegalArgumentException exception) {
            return -1;
        }
    }
}
