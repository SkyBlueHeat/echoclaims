package io.github.skyblueheat.echoclaims.integration;

import org.bukkit.inventory.ItemStack;

/**
 * Project-owned interface for serializing and deserializing Bukkit ItemStack data.
 *
 * <p>The current implementation uses Paper's byte serialization APIs, but the interface
 * allows the format to change without affecting callers. All methods must be called on
 * the server thread because they access live Bukkit objects.</p>
 *
 * <p>The serialized form is a Base64-encoded string that embeds a format version prefix,
 * enabling forward-compatible evolution of the serialization format.</p>
 */
public interface ItemSerializer {

    /**
     * Serializes an ItemStack into a versioned, Base64-encoded string.
     *
     * @param itemStack the item to serialize; may be null or air
     * @return the serialized form, or an empty string for null or air items
     * @throws ItemSerializationException if the payload exceeds the maximum size
     *         or the item cannot be serialized
     */
    String serialize(ItemStack itemStack) throws ItemSerializationException;

    /**
     * Deserializes a previously serialized string back into an ItemStack.
     *
     * @param data the serialized form; may be empty (returns null)
     * @return the reconstructed ItemStack, or null for empty data
     * @throws ItemSerializationException if the data is corrupted, uses an
     *         unsupported format version, or cannot be deserialized
     */
    ItemStack deserialize(String data) throws ItemSerializationException;

    /**
     * @return the format version embedded in serialized output
     */
    int formatVersion();

    /**
     * @return the maximum allowed serialized payload size in bytes (before Base64 encoding)
     */
    int maxPayloadBytes();
}
