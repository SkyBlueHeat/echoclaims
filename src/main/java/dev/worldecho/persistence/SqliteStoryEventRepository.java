package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.memory.MemoryEventType;
import dev.worldecho.domain.memory.StoryMemoryEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SqliteStoryEventRepository implements StoryEventRepository {

    private static final String INSERT_SQL = """
            INSERT OR IGNORE INTO story_events (
                id, event_type, occurred_at, world_id, x, y, z, player_id,
                actor_provider, actor_content_id, actor_runtime_id,
                item_provider, item_content_id, item_snapshot, details
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_RECENT_SQL = """
            SELECT id, event_type, occurred_at, world_id, x, y, z, player_id,
                   actor_provider, actor_content_id, actor_runtime_id,
                   item_provider, item_content_id, item_snapshot, details
            FROM story_events
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
            """;

    private final DatabaseManager databaseManager;

    public SqliteStoryEventRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public void insert(StoryMemoryEvent event) throws SQLException {
        insertAll(List.of(event));
    }

    @Override
    public int insertAll(Collection<StoryMemoryEvent> events) throws SQLException {
        if (events.isEmpty()) {
            return 0;
        }

        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                for (StoryMemoryEvent event : events) {
                    bind(statement, event);
                    statement.addBatch();
                }
                int stored = 0;
                for (int result : statement.executeBatch()) {
                    stored += Math.max(0, result);
                }
                connection.commit();
                return stored;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public List<StoryMemoryEvent> findRecent(int count) throws SQLException {
        List<StoryMemoryEvent> result = new ArrayList<>();

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_RECENT_SQL)) {
            statement.setInt(1, Math.max(1, count));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }

        return List.copyOf(result);
    }

    @Override
    public long count() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM story_events");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private void bind(PreparedStatement statement, StoryMemoryEvent event) throws SQLException {
        statement.setString(1, event.id().toString());
        statement.setString(2, event.type().name());
        statement.setLong(3, event.occurredAt().toEpochMilli());
        statement.setString(4, event.worldId().toString());
        statement.setInt(5, event.x());
        statement.setInt(6, event.y());
        statement.setInt(7, event.z());
        statement.setString(8, event.playerId().toString());
        statement.setString(9, event.actor().providerId());
        statement.setString(10, event.actor().contentId());
        statement.setString(11, event.actorRuntimeId());

        if (event.item() == null) {
            statement.setNull(12, Types.VARCHAR);
            statement.setNull(13, Types.VARCHAR);
        } else {
            statement.setString(12, event.item().providerId());
            statement.setString(13, event.item().contentId());
        }

        statement.setString(14, event.itemSnapshot());
        statement.setString(15, event.details());
    }

    private StoryMemoryEvent map(ResultSet resultSet) throws SQLException {
        String itemProvider = resultSet.getString("item_provider");
        String itemContentId = resultSet.getString("item_content_id");
        ContentKey item = itemProvider == null || itemContentId == null
                ? null
                : new ContentKey(itemProvider, itemContentId);

        return new StoryMemoryEvent(
                UUID.fromString(resultSet.getString("id")),
                MemoryEventType.valueOf(resultSet.getString("event_type")),
                Instant.ofEpochMilli(resultSet.getLong("occurred_at")),
                UUID.fromString(resultSet.getString("world_id")),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"),
                UUID.fromString(resultSet.getString("player_id")),
                new ContentKey(
                        resultSet.getString("actor_provider"),
                        resultSet.getString("actor_content_id")
                ),
                resultSet.getString("actor_runtime_id"),
                item,
                resultSet.getString("item_snapshot"),
                resultSet.getString("details")
        );
    }
}
