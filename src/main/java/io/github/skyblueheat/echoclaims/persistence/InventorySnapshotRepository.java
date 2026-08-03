package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link InventorySnapshot} persistence.
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads, never from
 * the server main thread. All writes related to one snapshot must be transactional: the
 * snapshot row and every item row are inserted atomically, or none are.</p>
 */
public interface InventorySnapshotRepository {

    /**
     * Inserts a snapshot and all of its items atomically.
     *
     * @throws java.sql.SQLException if the insert fails
     */
    void insert(InventorySnapshot snapshot) throws java.sql.SQLException;

    /**
     * Finds a snapshot by its UUID, including all items.
     *
     * @return the snapshot, or an empty optional if not found
     * @throws java.sql.SQLException if the query fails
     */
    Optional<InventorySnapshot> findById(UUID id) throws java.sql.SQLException;

    /**
     * Finds recent snapshots for a player, ordered by captured time descending.
     *
     * @param playerUuid the player UUID
     * @param limit maximum number of results
     * @throws java.sql.SQLException if the query fails
     */
    List<InventorySnapshot> findRecentByPlayer(UUID playerUuid, int limit) throws java.sql.SQLException;

    /**
     * Counts all stored snapshots.
     *
     * @throws java.sql.SQLException if the query fails
     */
    long count() throws java.sql.SQLException;
}
