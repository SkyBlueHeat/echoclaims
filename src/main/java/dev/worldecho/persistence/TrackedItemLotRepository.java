package dev.worldecho.persistence;

import dev.worldecho.domain.item.LotLineageEntry;
import dev.worldecho.domain.item.LotCompatibilityFingerprint;
import dev.worldecho.domain.item.OwnershipSubjectType;
import dev.worldecho.domain.item.TrackedItemLot;
import dev.worldecho.domain.item.TrackedItemLotId;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for {@link TrackedItemLot} persistence.
 */
public interface TrackedItemLotRepository {

    enum CreateResult { CREATED, ALREADY_EXISTS }

    enum ReconcileResult { SUCCESS, FAILURE }

    CreateResult create(TrackedItemLot lot) throws SQLException;

    /**
     * Atomically reconciles all LOT aggregates for a single owner in one transaction.
     *
     * <p>Within the transaction:
     * <ol>
     *   <li>Upsert each observed fingerprint+amount pair (create if absent, update if present).</li>
     *   <li>Zero out existing lots for this owner whose fingerprint is absent from the snapshot.</li>
     *   <li>Update the owner display snapshot.</li>
     * </ol>
     * If any SQL operation fails, the entire transaction is rolled back.
     *
     * @param ownerType             normalized owner type token
     * @param ownerStableId         normalized stable owner identifier
     * @param ownerDisplaySnapshot  display name at observation time
     * @param observedAmountsByFingerprint  map of fingerprint → total observed amount
     * @param observedAt            timestamp for last_seen_at update
     * @return {@link ReconcileResult#SUCCESS} or {@link ReconcileResult#FAILURE}
     */
    ReconcileResult reconcileOwnerAggregates(
            OwnershipSubjectType ownerType,
            String ownerStableId,
            String ownerDisplaySnapshot,
            Map<LotCompatibilityFingerprint, Integer> observedAmountsByFingerprint,
            Instant observedAt
    );

    Optional<TrackedItemLot> findById(TrackedItemLotId lotId) throws SQLException;

    Optional<TrackedItemLot> findByFingerprint(LotCompatibilityFingerprint fingerprint) throws SQLException;

    Optional<TrackedItemLot> findByFingerprintAndOwner(
            LotCompatibilityFingerprint fingerprint, String ownerSubject) throws SQLException;

    Optional<TrackedItemLot> findByOwnerAndFingerprint(
            OwnershipSubjectType ownerType, String ownerStableId,
            LotCompatibilityFingerprint fingerprint) throws SQLException;

    List<TrackedItemLot> findAllByOwner(
            OwnershipSubjectType ownerType, String ownerStableId) throws SQLException;

    void updateOwnerDisplaySnapshot(TrackedItemLotId lotId, String displayName) throws SQLException;

    boolean exists(TrackedItemLotId lotId) throws SQLException;

    void updateAmount(TrackedItemLotId lotId, int newAmount) throws SQLException;

    void observe(TrackedItemLotId lotId, java.time.Instant lastSeenAt) throws SQLException;

    int count() throws SQLException;

    void appendLineage(LotLineageEntry entry) throws SQLException;

    Optional<LotLineageEntry> findLineageByIdempotencyKey(TrackedItemLotId lotId, String idempotencyKey) throws SQLException;

    List<LotLineageEntry> findLineage(TrackedItemLotId lotId) throws SQLException;
}
