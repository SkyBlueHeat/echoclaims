package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
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
 * SQLite implementation of {@link ClaimStore}.
 *
 * <p>{@link #createClaim} and {@link #transitionClaim} execute all SQL statements inside
 * a single transaction. If any statement fails, the entire transaction is rolled back,
 * guaranteeing no orphan claims or audit entries.</p>
 */
public final class SqliteClaimStore implements ClaimStore {

    private final DatabaseManager databaseManager;

    public SqliteClaimStore(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    private static final String CLAIM_COLUMNS =
            "id, public_reference, incident_id, player_uuid, status, source, "
                    + "description, created_at, submitted_at, cancelled_at, version, metadata";

    @Override
    public void createClaim(Claim claim, ClaimAuditEntry auditEntry) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertClaimRow(connection, claim);
                insertAuditRow(connection, auditEntry);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public boolean transitionClaim(
            UUID claimId,
            ClaimStatus newStatus,
            long submittedAt,
            long cancelledAt,
            int expectedVersion,
            ClaimAuditEntry auditEntry
    ) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(auditEntry, "auditEntry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean updated = updateClaimStatus(
                        connection, claimId, newStatus, submittedAt, cancelledAt, expectedVersion);
                if (!updated) {
                    connection.rollback();
                    return false;
                }
                insertAuditRow(connection, auditEntry);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public Optional<Claim> findClaimById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + CLAIM_COLUMNS + " FROM claims WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readClaim(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Claim> findClaimByPublicReference(String publicReference) throws SQLException {
        Objects.requireNonNull(publicReference, "publicReference");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + CLAIM_COLUMNS + " FROM claims WHERE public_reference = ?")) {
            statement.setString(1, publicReference.strip());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readClaim(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Claim> findRecentClaimsByPlayer(UUID playerUuid, int limit) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + CLAIM_COLUMNS + " FROM claims "
                             + "WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, safeLimit);
            return readClaimList(statement);
        }
    }

    @Override
    public List<Claim> findOpenClaimsByIncident(UUID incidentId) throws SQLException {
        Objects.requireNonNull(incidentId, "incidentId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + CLAIM_COLUMNS + " FROM claims "
                             + "WHERE incident_id = ? AND status IN ('DRAFT', 'SUBMITTED') "
                             + "ORDER BY created_at DESC")) {
            statement.setString(1, incidentId.toString());
            return readClaimList(statement);
        }
    }

    @Override
    public List<ClaimAuditEntry> findAuditEntries(UUID claimId) throws SQLException {
        Objects.requireNonNull(claimId, "claimId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, claim_id, actor_type, actor_uuid, action, reason, "
                             + "recorded_at, claim_version, metadata "
                             + "FROM claim_audit_entries WHERE claim_id = ? "
                             + "ORDER BY recorded_at ASC")) {
            statement.setString(1, claimId.toString());
            List<ClaimAuditEntry> entries = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(readAuditEntry(resultSet));
                }
            }
            return List.copyOf(entries);
        }
    }

    @Override
    public long countClaimsByPlayerAndStatus(UUID playerUuid, ClaimStatus status) throws SQLException {
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
    public long countClaims() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claims");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    @Override
    public long countClaimsByStatus(ClaimStatus status) throws SQLException {
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

    @Override
    public long countAuditEntries() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claim_audit_entries");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    @Override
    public List<Claim> findClaimsByStatus(ClaimStatus status, int limit) throws SQLException {
        Objects.requireNonNull(status, "status");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + CLAIM_COLUMNS + " FROM claims "
                             + "WHERE status = ? ORDER BY created_at ASC LIMIT ?")) {
            statement.setString(1, status.name());
            statement.setInt(2, safeLimit);
            return readClaimList(statement);
        }
    }

    private static void insertClaimRow(Connection connection, Claim claim) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
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

    private static void insertAuditRow(Connection connection, ClaimAuditEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO claim_audit_entries (id, claim_id, actor_type, actor_uuid, "
                        + "action, reason, recorded_at, claim_version, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.claimId().toString());
            statement.setString(3, entry.actorType().name());
            if (entry.actorUuid() != null) {
                statement.setString(4, entry.actorUuid().toString());
            } else {
                statement.setNull(4, Types.VARCHAR);
            }
            statement.setString(5, entry.action().name());
            statement.setString(6, entry.reason());
            statement.setLong(7, entry.recordedAt());
            statement.setInt(8, entry.claimVersion());
            statement.setString(9, MapCodec.encodeStringMap(entry.metadata()));
            statement.executeUpdate();
        }
    }

    private static boolean updateClaimStatus(
            Connection connection, UUID claimId, ClaimStatus newStatus,
            long submittedAt, long cancelledAt, int expectedVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
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

    private static ClaimAuditEntry readAuditEntry(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        UUID claimId = UUID.fromString(resultSet.getString("claim_id"));
        var actorType = io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType
                .valueOf(resultSet.getString("actor_type"));
        String actorUuidStr = resultSet.getString("actor_uuid");
        UUID actorUuid = actorUuidStr != null ? UUID.fromString(actorUuidStr) : null;
        var action = io.github.skyblueheat.echoclaims.domain.claim.ClaimAction
                .valueOf(resultSet.getString("action"));
        String reason = resultSet.getString("reason");
        long recordedAt = resultSet.getLong("recorded_at");
        int claimVersion = resultSet.getInt("claim_version");
        String metadataEncoded = resultSet.getString("metadata");

        return new ClaimAuditEntry(
                id, claimId, actorType, actorUuid, action,
                reason, recordedAt, claimVersion,
                MapCodec.decodeStringMap(metadataEncoded)
        );
    }
}
