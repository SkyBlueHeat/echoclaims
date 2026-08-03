package dev.worldecho.persistence;

import dev.worldecho.domain.memory.StoryMemoryEvent;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * Durable storage for memory events. All methods block and must run off the server thread.
 */
public interface StoryEventRepository {

    void insert(StoryMemoryEvent event) throws SQLException;

    /**
     * Inserts a batch in a single transaction.
     *
     * @return the number of stored events
     */
    int insertAll(Collection<StoryMemoryEvent> events) throws SQLException;

    List<StoryMemoryEvent> findRecent(int count) throws SQLException;

    long count() throws SQLException;
}
