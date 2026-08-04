package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of {@link ClaimRepository}.
 *
 * <p>Every method blocks on disk I/O and must therefore be called from the EchoClaims
 * writer thread, never from the server thread.</p>
 */
public final class SqliteClaimRepository implements ClaimRepository {

    private final DatabaseManager databaseManager;

    public SqliteClaimRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String SELECT_COLUMNS =
            "id, public_reference, incident_id, player_uuid, status, source, "
                    + "description, created_at, submitted_at, cancelled_at, version, metadata";

    @Override
    public void insert(Claim claim) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO claims (id, public_reference, incident_id, player_uuid, "
                             + "status, source, description, created_at, submitted_at, "
                             + "cancelled_at, version, metadata) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, claim.id().toString());
            statement.setString(2, claim.publicReference());
            statement.setString(3, claim.incidentId().toString());
            statement.setString(4, claim.playerUuid().toString());
            statement.setString(5, claim.status().name());
            statement.setString(6, claim.source().name());
            statement.setString(7, claim.description());
            statement.setLong(8, claim.createdAt());
            statement.setLong(9, claim.submittedAt());
            statement.setLong(10, claim.cancelledAt());
            statement.setInt(11, claim.version());
            statement.setString(12, MapCodec.encodeStringMap(claim.metadata()));
            statement.executeUpdate();
        }
    }

    @Override
    public boolean updateStatus(UUID claimId, ClaimStatus newStatus, long submittedAt,
                                long cancelledAt, int expectedVersion) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(newStatus, "newStatus");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE claims SET status = ?, submitted_at = ?, cancelled_at = ?, "
                             + "version = version + 1 WHERE id = ? AND version = ?")) {
            statement.setString(1, newStatus.name());
            statement.setLong(2, submittedAt);
            statement.setLong(3, cancelledAt);
            statement.setString(4, claimId.toString());
            statement.setInt(5, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<Claim> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + SELECT_COLUMNS + " FROM claims WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readClaim(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Claim> findByPublicReference(String publicReference) throws SQLException {
        Objects.requireNonNull(publicReference, "publicReference");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + SELECT_COLUMNS + " FROM claims WHERE public_reference = ?")) {
            statement.setString(1, publicReference.strip());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readClaim(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Claim> findRecentByPlayer(UUID playerUuid, int limit) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + SELECT_COLUMNS + " FROM claims "
                             + "WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, safeLimit);
            return readClaimList(statement);
        }
    }

    @Override
    public List<Claim> findOpenByIncident(UUID incidentId) throws SQLException {
        Objects.requireNonNull(incidentId, "incidentId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + SELECT_COLUMNS + " FROM claims "
                             + "WHERE incident_id = ? AND status IN ('DRAFT', 'SUBMITTED') "
                             + "ORDER BY created_at DESC")) {
            statement.setString(1, incidentId.toString());
            return readClaimList(statement);
        }
    }

    @Override
    public long countByPlayerAndStatus(UUID playerUuid, ClaimStatus status) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(status, "status");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claims WHERE player_uuid = ? AND status = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, status.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claims");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    @Override
    public long countByStatus(ClaimStatus status) throws SQLException {
        Objects.requireNonNull(status, "status");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claims WHERE status = ?")) {
            statement.setString(1, status.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private static List<Claim> readClaimList(PreparedStatement statement) throws SQLException {
        List<Claim> claims = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                claims.add(readClaim(resultSet));
            }
        }
        return List.copyOf(claims);
    }

    private static Claim readClaim(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        String publicReference = resultSet.getString("public_reference");
        UUID incidentId = UUID.fromString(resultSet.getString("incident_id"));
        UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
        ClaimStatus status = ClaimStatus.valueOf(resultSet.getString("status"));
        ClaimSource source = ClaimSource.valueOf(resultSet.getString("source"));
        String description = resultSet.getString("description");
        long createdAt = resultSet.getLong("created_at");
        long submittedAt = resultSet.getLong("submitted_at");
        long cancelledAt = resultSet.getLong("cancelled_at");
        int version = resultSet.getInt("version");
        String metadataEncoded = resultSet.getString("metadata");

        return new Claim(
                id, publicReference, incidentId, playerUuid,
                status, source, description,
                createdAt, submittedAt, cancelledAt,
                version, MapCodec.decodeStringMap(metadataEncoded)
        );
    }
}
