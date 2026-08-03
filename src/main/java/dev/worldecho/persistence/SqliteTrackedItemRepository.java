package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TrackedItemRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class SqliteTrackedItemRepository implements TrackedItemRepository {

    private static final String INSERT_SQL = """
            INSERT OR IGNORE INTO tracked_items (
                item_id, created_at, first_seen_at, last_seen_at,
                content_key, provider_id, initial_material, initial_custom_name,
                initial_value_score, tracking_reason, created_by_subject
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT item_id, created_at, first_seen_at, last_seen_at,
                   content_key, provider_id, initial_material, initial_custom_name,
                   initial_value_score, tracking_reason, created_by_subject
            FROM tracked_items
            WHERE item_id = ?
            """;

    private static final String EXISTS_SQL = "SELECT 1 FROM tracked_items WHERE item_id = ?";

    private static final String OBSERVE_SQL = "UPDATE tracked_items SET last_seen_at = ? WHERE item_id = ?";

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM tracked_items";

    private final DatabaseManager databaseManager;

    public SqliteTrackedItemRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public CreateResult create(TrackedItemRecord record) throws SQLException {
        Objects.requireNonNull(record, "record");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, record.itemId().toString());
            statement.setLong(2, record.createdAt().toEpochMilli());
            statement.setLong(3, record.firstSeenAt().toEpochMilli());
            statement.setLong(4, record.lastSeenAt().toEpochMilli());
            statement.setString(5, record.contentKey().toString());
            statement.setString(6, record.providerId());
            statement.setString(7, record.initialMaterial());
            statement.setString(8, record.initialCustomName());
            if (record.initialValueScore() == null) {
                statement.setNull(9, Types.INTEGER);
            } else {
                statement.setInt(9, record.initialValueScore());
            }
            statement.setString(10, record.trackingReason());
            statement.setString(11, record.createdBySubject());
            int rows = statement.executeUpdate();
            return rows > 0 ? CreateResult.CREATED : CreateResult.ALREADY_EXISTS;
        }
    }

    @Override
    public Optional<TrackedItemRecord> findById(TrackedItemId itemId) throws SQLException {
        Objects.requireNonNull(itemId, "itemId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setString(1, itemId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean exists(TrackedItemId itemId) throws SQLException {
        Objects.requireNonNull(itemId, "itemId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_SQL)) {
            statement.setString(1, itemId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void observe(TrackedItemId itemId, long lastSeenEpochMilli) throws SQLException {
        Objects.requireNonNull(itemId, "itemId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(OBSERVE_SQL)) {
            statement.setLong(1, lastSeenEpochMilli);
            statement.setString(2, itemId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static TrackedItemRecord map(ResultSet rs) throws SQLException {
        Integer valueScore = rs.getObject("initial_value_score") == null
                ? null : rs.getInt("initial_value_score");
        return new TrackedItemRecord(
                TrackedItemId.parse(rs.getString("item_id")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("first_seen_at")),
                Instant.ofEpochMilli(rs.getLong("last_seen_at")),
                ContentKey.parse(rs.getString("content_key")),
                rs.getString("provider_id"),
                rs.getString("initial_material"),
                rs.getString("initial_custom_name"),
                valueScore,
                rs.getString("tracking_reason"),
                rs.getString("created_by_subject")
        );
    }
}
