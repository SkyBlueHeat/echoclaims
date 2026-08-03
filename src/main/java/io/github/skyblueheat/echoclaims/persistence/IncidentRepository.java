package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Incident} persistence.
 *
 * <p>Implementations are called exclusively from EchoClaims worker threads, never from
 * the server main thread. The deduplication key uniqueness constraint provides the final
 * barrier against duplicate incidents.</p>
 */
public interface IncidentRepository {

    /**
     * Inserts an incident.
     *
     * @throws java.sql.SQLException if the insert fails, including duplicate deduplication key
     */
    void insert(Incident incident) throws java.sql.SQLException;

    /**
     * Updates the post-event snapshot reference on an existing incident.
     *
     * @param incidentId the incident UUID
     * @param postEventSnapshotUuid the post-event snapshot UUID to set
     * @throws java.sql.SQLException if the update fails
     */
    void updatePostEventSnapshot(UUID incidentId, UUID postEventSnapshotUuid) throws java.sql.SQLException;

    /**
     * Finds an incident by its UUID.
     *
     * @return the incident, or an empty optional if not found
     * @throws java.sql.SQLException if the query fails
     */
    Optional<Incident> findById(UUID id) throws java.sql.SQLException;

    /**
     * Finds recent incidents for a player, ordered by occurred time descending.
     *
     * @param playerUuid the player UUID
     * @param limit maximum number of results
     * @throws java.sql.SQLException if the query fails
     */
    List<Incident> findRecentByPlayer(UUID playerUuid, int limit) throws java.sql.SQLException;

    /**
     * Finds incidents by type within a time range, ordered by occurred time descending.
     *
     * @param type the incident type
     * @param from inclusive start timestamp (epoch millis)
     * @param to inclusive end timestamp (epoch millis)
     * @param limit maximum number of results
     * @throws java.sql.SQLException if the query fails
     */
    List<Incident> findByTypeAndTimeRange(IncidentType type, long from, long to, int limit) throws java.sql.SQLException;

    /**
     * Counts all stored incidents.
     *
     * @throws java.sql.SQLException if the query fails
     */
    long count() throws java.sql.SQLException;
}
