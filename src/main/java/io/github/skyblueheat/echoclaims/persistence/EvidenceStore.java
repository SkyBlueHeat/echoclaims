package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Atomic persistence of a complete death-evidence unit.
 *
 * <p>Implementations must insert the inventory snapshot, all snapshot items, and the
 * incident in a single database transaction. Either all three persist or none persist.
 * A failed incident insert must not leave an orphan snapshot.</p>
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads, never from
 * the server main thread.</p>
 */
public interface EvidenceStore {

    /**
     * Inserts a snapshot (with all items) and its linked incident atomically.
     *
     * @throws SQLException if any insert fails; the entire operation is rolled back
     */
    void insertDeathEvidence(InventorySnapshot snapshot, Incident incident) throws SQLException;

    /**
     * Inserts a post-respawn snapshot and links it to an existing incident atomically.
     *
     * @param incidentId the existing incident to link
     * @param snapshot the post-respawn snapshot to insert
     * @throws SQLException if the snapshot insert or the incident update fails
     */
    void insertPostRespawnSnapshot(UUID incidentId, InventorySnapshot snapshot) throws SQLException;
}
