package dev.worldecho.persistence;

import dev.worldecho.domain.item.OwnershipLedgerEntry;
import dev.worldecho.domain.item.OwnershipState;
import dev.worldecho.domain.item.OwnershipSubject;
import dev.worldecho.domain.item.TrackedItemId;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Durable append-only ownership ledger.
 * All methods block and must run off the server thread.
 */
public interface OwnershipLedgerRepository {

    enum AppendResult {
        APPENDED,
        IDEMPOTENT_REPLAY,
        CONFLICT
    }

    AppendResult append(OwnershipLedgerEntry entry) throws SQLException;

    Optional<OwnershipState> findCurrentOwnership(TrackedItemId itemId) throws SQLException;

    List<OwnershipLedgerEntry> findHistory(TrackedItemId itemId, int limit) throws SQLException;

    long countHistory(TrackedItemId itemId) throws SQLException;

    Optional<OwnershipLedgerEntry> findByIdempotencyKey(TrackedItemId itemId, String idempotencyKey) throws SQLException;

    long count() throws SQLException;
}
