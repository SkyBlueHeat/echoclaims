package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.persistence.IncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only evidence lookup for administrative commands.
 *
 * <p>All methods block on disk I/O and must be called from the query executor, never
 * from the server main thread.</p>
 */
public class EvidenceLookupService {

    private final IncidentRepository incidentRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final int maxRecentResults;

    public EvidenceLookupService(
            IncidentRepository incidentRepository,
            InventorySnapshotRepository snapshotRepository,
            int maxRecentResults
    ) {
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository");
        this.maxRecentResults = Math.max(1, Math.min(maxRecentResults, 100));
    }

    public List<Incident> findIncidentsByPlayer(UUID playerUuid) throws java.sql.SQLException {
        return incidentRepository.findRecentByPlayer(playerUuid, maxRecentResults);
    }

    public Optional<Incident> findIncidentById(UUID id) throws java.sql.SQLException {
        return incidentRepository.findById(id);
    }

    public Optional<InventorySnapshot> findSnapshotById(UUID id) throws java.sql.SQLException {
        return snapshotRepository.findById(id);
    }

    public int maxRecentResults() {
        return maxRecentResults;
    }
}
