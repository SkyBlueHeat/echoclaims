package io.github.skyblueheat.echoclaims.integration;

/**
 * Thrown when item serialization or deserialization fails.
 *
 * <p>This exception covers corrupted payloads, oversized data, unsupported format
 * versions, and any other error that prevents a safe round-trip.</p>
 */
public class ItemSerializationException extends Exception {

    public ItemSerializationException(String message) {
        super(message);
    }

    public ItemSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
