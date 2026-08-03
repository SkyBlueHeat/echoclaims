package dev.worldecho.persistence;

import dev.worldecho.domain.item.LotOwnershipLedgerEntry;
import dev.worldecho.domain.item.LotOwnershipState;
import dev.worldecho.domain.item.TrackedItemLotId;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Repository for lot ownership ledger persistence.
 */
public interface LotOwnershipLedgerRepository {

    enum AppendResult { APPENDED, IDEMPOTENT_REPLAY, CONFLICT }

    AppendResult append(LotOwnershipLedgerEntry entry) throws SQLException;

    Optional<LotOwnershipState> findCurrentOwnership(TrackedItemLotId lotId) throws SQLException;

    Optional<LotOwnershipLedgerEntry> findByIdempotencyKey(TrackedItemLotId lotId, String idempotencyKey) throws SQLException;

    List<LotOwnershipLedgerEntry> findHistory(TrackedItemLotId lotId, int limit) throws SQLException;

    long countHistory(TrackedItemLotId lotId) throws SQLException;

    long countAll() throws SQLException;
}
