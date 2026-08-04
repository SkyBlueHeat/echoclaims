package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of {@link IncidentRepository}.
 *
 * <p>Every method blocks on disk I/O and must therefore be called from the EchoClaims
 * writer thread, never from the server thread. The deduplication key uniqueness
 * constraint provides the final barrier against duplicate incidents.</p>
 */
public final class SqliteIncidentRepository implements IncidentRepository {

    private final DatabaseManager databaseManager;

    public SqliteIncidentRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public void insert(Incident incident) throws SQLException {
        Objects.requireNonNull(incident, "incident");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO incidents (id, incident_type, player_uuid, occurred_at, "
                             + "world_id, x, y, z, cause, killer_player_uuid, killer_entity_key, "
                             + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, "
                             + "metadata, deduplication_key) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, incident.id().toString());
            statement.setString(2, incident.type().name());
            statement.setString(3, incident.playerUuid().toString());
            statement.setLong(4, incident.occurredAt());
            statement.setString(5, incident.worldId());
            if (incident.hasCoordinates()) {
                statement.setInt(6, incident.coordinates().x());
                statement.setInt(7, incident.coordinates().y());
                statement.setInt(8, incident.coordinates().z());
            } else {
                statement.setNull(6, java.sql.Types.INTEGER);
                statement.setNull(7, java.sql.Types.INTEGER);
                statement.setNull(8, java.sql.Types.INTEGER);
            }
            statement.setString(9, incident.cause());
            if (incident.hasKillerPlayer()) {
                statement.setString(10, incident.killerPlayerUuid().toString());
            } else {
                statement.setNull(10, java.sql.Types.VARCHAR);
            }
            statement.setString(11, incident.killerEntityKey());
            statement.setString(12, incident.preEventSnapshotUuid().toString());
            if (incident.hasPostEventSnapshot()) {
                statement.setString(13, incident.postEventSnapshotUuid().toString());
            } else {
                statement.setNull(13, java.sql.Types.VARCHAR);
            }
            statement.setString(14, incident.status().name());
            statement.setString(15, MapCodec.encodeStringMap(incident.metadata()));
            statement.setString(16, incident.deduplicationKey());
            statement.executeUpdate();
        }
    }

    @Override
    public void updatePostEventSnapshot(UUID incidentId, UUID postEventSnapshotUuid) throws SQLException {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(postEventSnapshotUuid, "postEventSnapshotUuid");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE incidents SET post_event_snapshot_uuid = ? WHERE id = ?")) {
            statement.setString(1, postEventSnapshotUuid.toString());
            statement.setString(2, incidentId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Incident> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, incident_type, player_uuid, occurred_at, world_id, x, y, z, "
                             + "cause, killer_player_uuid, killer_entity_key, "
                             + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, "
                             + "metadata, deduplication_key FROM incidents WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readIncident(resultSet));
            }
        }
    }

    @Override
    public List<Incident> findRecentByPlayer(UUID playerUuid, int limit) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, incident_type, player_uuid, occurred_at, world_id, x, y, z, "
                             + "cause, killer_player_uuid, killer_entity_key, "
                             + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, "
                             + "metadata, deduplication_key FROM incidents "
                             + "WHERE player_uuid = ? ORDER BY occurred_at DESC LIMIT ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, safeLimit);
            return readIncidentList(statement);
        }
    }

    @Override
    public List<Incident> findByTypeAndTimeRange(IncidentType type, long from, long to, int limit) throws SQLException {
        Objects.requireNonNull(type, "type");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, incident_type, player_uuid, occurred_at, world_id, x, y, z, "
                             + "cause, killer_player_uuid, killer_entity_key, "
                             + "pre_event_snapshot_uuid, post_event_snapshot_uuid, status, "
                             + "metadata, deduplication_key FROM incidents "
                             + "WHERE incident_type = ? AND occurred_at >= ? AND occurred_at <= ? "
                             + "ORDER BY occurred_at DESC LIMIT ?")) {
            statement.setString(1, type.name());
            statement.setLong(2, from);
            statement.setLong(3, to);
            statement.setInt(4, safeLimit);
            return readIncidentList(statement);
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM incidents");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static List<Incident> readIncidentList(PreparedStatement statement) throws SQLException {
        List<Incident> incidents = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                incidents.add(readIncident(resultSet));
            }
        }
        return List.copyOf(incidents);
    }

    private static Incident readIncident(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        IncidentType type = IncidentType.valueOf(resultSet.getString("incident_type"));
        UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
        long occurredAt = resultSet.getLong("occurred_at");
        String worldId = resultSet.getString("world_id");
        Coordinates coordinates = null;
        int x = resultSet.getInt("x");
        if (!resultSet.wasNull()) {
            int y = resultSet.getInt("y");
            int z = resultSet.getInt("z");
            coordinates = new Coordinates(x, y, z);
        }
        String cause = resultSet.getString("cause");
        String killerPlayerStr = resultSet.getString("killer_player_uuid");
        UUID killerPlayerUuid = killerPlayerStr != null ? UUID.fromString(killerPlayerStr) : null;
        String killerEntityKey = resultSet.getString("killer_entity_key");
        UUID preEventSnapshotUuid = UUID.fromString(resultSet.getString("pre_event_snapshot_uuid"));
        String postEventStr = resultSet.getString("post_event_snapshot_uuid");
        UUID postEventSnapshotUuid = postEventStr != null ? UUID.fromString(postEventStr) : null;
        IncidentStatus status = IncidentStatus.valueOf(resultSet.getString("status"));
        String metadataEncoded = resultSet.getString("metadata");
        String deduplicationKey = resultSet.getString("deduplication_key");

        return new Incident(
                id, type, playerUuid, occurredAt, worldId, coordinates,
                cause, killerPlayerUuid, killerEntityKey,
                preEventSnapshotUuid, postEventSnapshotUuid,
                status, MapCodec.decodeStringMap(metadataEncoded), deduplicationKey
        );
    }
}
