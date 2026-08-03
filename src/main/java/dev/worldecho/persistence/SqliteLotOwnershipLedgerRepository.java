package dev.worldecho.persistence;

import dev.worldecho.domain.item.LotOwnershipLedgerEntry;
import dev.worldecho.domain.item.LotOwnershipState;
import dev.worldecho.domain.item.OwnershipSubject;
import dev.worldecho.domain.item.OwnershipSubjectType;
import dev.worldecho.domain.item.OwnershipTransitionReason;
import dev.worldecho.domain.item.TrackedItemLotId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SqliteLotOwnershipLedgerRepository implements LotOwnershipLedgerRepository {

    private static final String INSERT_SQL = """
            INSERT INTO item_lot_ownership_ledger (
                entry_id, lot_id, sequence_number,
                previous_subject_type, previous_subject_id, previous_subject_display,
                new_subject_type, new_subject_id, new_subject_display,
                transition_reason, occurred_at, recorded_at,
                source, story_event_id, idempotency_key, notes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_CURRENT_SQL = """
            SELECT entry_id, lot_id, sequence_number,
                   previous_subject_type, previous_subject_id, previous_subject_display,
                   new_subject_type, new_subject_id, new_subject_display,
                   transition_reason, occurred_at, recorded_at,
                   source, story_event_id, idempotency_key, notes
            FROM item_lot_ownership_ledger
            WHERE lot_id = ?
            ORDER BY sequence_number DESC
            LIMIT 1
            """;

    private static final String FIND_HISTORY_SQL = """
            SELECT entry_id, lot_id, sequence_number,
                   previous_subject_type, previous_subject_id, previous_subject_display,
                   new_subject_type, new_subject_id, new_subject_display,
                   transition_reason, occurred_at, recorded_at,
                   source, story_event_id, idempotency_key, notes
            FROM item_lot_ownership_ledger
            WHERE lot_id = ?
            ORDER BY sequence_number DESC
            LIMIT ?
            """;

    private static final String COUNT_HISTORY_SQL = "SELECT COUNT(*) FROM item_lot_ownership_ledger WHERE lot_id = ?";

    private static final String FIND_BY_IDEMPOTENCY_SQL = """
            SELECT entry_id, lot_id, sequence_number,
                   previous_subject_type, previous_subject_id, previous_subject_display,
                   new_subject_type, new_subject_id, new_subject_display,
                   transition_reason, occurred_at, recorded_at,
                   source, story_event_id, idempotency_key, notes
            FROM item_lot_ownership_ledger
            WHERE lot_id = ? AND idempotency_key = ?
            LIMIT 1
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM item_lot_ownership_ledger";

    private final DatabaseManager databaseManager;

    public SqliteLotOwnershipLedgerRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public AppendResult append(LotOwnershipLedgerEntry entry) throws SQLException {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = databaseManager.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                bind(statement, entry);
                statement.executeUpdate();
                connection.commit();
                return AppendResult.APPENDED;
            } catch (SQLException exception) {
                connection.rollback();
                String message = exception.getMessage();
                if (message != null && message.contains("idempotency_key")) {
                    return AppendResult.IDEMPOTENT_REPLAY;
                }
                if (message != null && message.contains("sequence_number")) {
                    return AppendResult.CONFLICT;
                }
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public Optional<LotOwnershipState> findCurrentOwnership(TrackedItemLotId lotId) throws SQLException {
        Objects.requireNonNull(lotId, "lotId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_CURRENT_SQL)) {
            statement.setString(1, lotId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    LotOwnershipLedgerEntry entry = map(rs);
                    long count = countHistory(lotId);
                    return Optional.of(new LotOwnershipState(
                            lotId,
                            entry.newSubject(),
                            entry.sequenceNumber(),
                            entry.transitionReason(),
                            entry.occurredAt(),
                            count
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<LotOwnershipLedgerEntry> findByIdempotencyKey(TrackedItemLotId lotId, String idempotencyKey)
            throws SQLException {
        Objects.requireNonNull(lotId, "lotId");
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return Optional.empty();
        }
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_IDEMPOTENCY_SQL)) {
            statement.setString(1, lotId.toString());
            statement.setString(2, idempotencyKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<LotOwnershipLedgerEntry> findHistory(TrackedItemLotId lotId, int limit) throws SQLException {
        Objects.requireNonNull(lotId, "lotId");
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<LotOwnershipLedgerEntry> entries = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_HISTORY_SQL)) {
            statement.setString(1, lotId.toString());
            statement.setInt(2, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(map(rs));
                }
            }
        }
        return List.copyOf(entries);
    }

    @Override
    public long countHistory(TrackedItemLotId lotId) throws SQLException {
        Objects.requireNonNull(lotId, "lotId");
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_HISTORY_SQL)) {
            statement.setString(1, lotId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    @Override
    public long countAll() throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static void bind(PreparedStatement statement, LotOwnershipLedgerEntry entry) throws SQLException {
        statement.setString(1, entry.entryId());
        statement.setString(2, entry.lotId().toString());
        statement.setInt(3, entry.sequenceNumber());

        if (entry.previousSubject() == null) {
            statement.setNull(4, Types.VARCHAR);
            statement.setNull(5, Types.VARCHAR);
            statement.setNull(6, Types.VARCHAR);
        } else {
            statement.setString(4, entry.previousSubject().type().name());
            statement.setString(5, entry.previousSubject().stableId());
            statement.setString(6, entry.previousSubject().displayName());
        }

        statement.setString(7, entry.newSubject().type().name());
        statement.setString(8, entry.newSubject().stableId());
        statement.setString(9, entry.newSubject().displayName());
        statement.setString(10, entry.transitionReason().name());
        statement.setLong(11, entry.occurredAt().toEpochMilli());
        statement.setLong(12, entry.recordedAt().toEpochMilli());
        statement.setString(13, entry.source());
        statement.setString(14, entry.storyEventId());
        statement.setString(15, entry.idempotencyKey());
        statement.setString(16, entry.notes());
    }

    private static LotOwnershipLedgerEntry map(ResultSet rs) throws SQLException {
        String prevType = rs.getString("previous_subject_type");
        String prevId = rs.getString("previous_subject_id");
        String prevDisplay = rs.getString("previous_subject_display");
        OwnershipSubject previous = null;
        if (prevType != null && prevId != null) {
            previous = new OwnershipSubject(
                    OwnershipSubjectType.valueOf(prevType),
                    prevId,
                    prevDisplay == null ? "" : prevDisplay
            );
        }

        return new LotOwnershipLedgerEntry(
                rs.getString("entry_id"),
                TrackedItemLotId.parse(rs.getString("lot_id")),
                rs.getInt("sequence_number"),
                previous,
                new OwnershipSubject(
                        OwnershipSubjectType.valueOf(rs.getString("new_subject_type")),
                        rs.getString("new_subject_id"),
                        rs.getString("new_subject_display")
                ),
                OwnershipTransitionReason.valueOf(rs.getString("transition_reason")),
                Instant.ofEpochMilli(rs.getLong("occurred_at")),
                Instant.ofEpochMilli(rs.getLong("recorded_at")),
                rs.getString("source"),
                rs.getString("story_event_id"),
                rs.getString("idempotency_key"),
                rs.getString("notes")
        );
    }
}
