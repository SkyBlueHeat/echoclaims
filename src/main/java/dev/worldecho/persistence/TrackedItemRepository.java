package dev.worldecho.persistence;

import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TrackedItemRecord;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Durable storage for tracked-item identity records.
 * All methods block and must run off the server thread.
 */
public interface TrackedItemRepository {

    enum CreateResult {
        CREATED,
        ALREADY_EXISTS
    }

    CreateResult create(TrackedItemRecord record) throws SQLException;

    Optional<TrackedItemRecord> findById(TrackedItemId itemId) throws SQLException;

    boolean exists(TrackedItemId itemId) throws SQLException;

    void observe(TrackedItemId itemId, long lastSeenEpochMilli) throws SQLException;

    long count() throws SQLException;
}
