package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite implementation of {@link ClaimAuditRepository}.
 *
 * <p>Every method blocks on disk I/O and must therefore be called from an EchoClaims
 * worker thread, never from the server thread.</p>
 */
public final class SqliteClaimAuditRepository implements ClaimAuditRepository {

    private final DatabaseManager databaseManager;

    public SqliteClaimAuditRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public void insert(ClaimAuditEntry entry) throws SQLException {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
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

    @Override
    public List<ClaimAuditEntry> findByClaimId(UUID claimId) throws SQLException {
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
                    entries.add(readEntry(resultSet));
                }
            }
            return List.copyOf(entries);
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM claim_audit_entries");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static ClaimAuditEntry readEntry(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        UUID claimId = UUID.fromString(resultSet.getString("claim_id"));
        ClaimActorType actorType = ClaimActorType.valueOf(resultSet.getString("actor_type"));
        String actorUuidStr = resultSet.getString("actor_uuid");
        UUID actorUuid = actorUuidStr != null ? UUID.fromString(actorUuidStr) : null;
        ClaimAction action = ClaimAction.valueOf(resultSet.getString("action"));
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
