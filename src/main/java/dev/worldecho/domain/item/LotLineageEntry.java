package dev.worldecho.domain.item;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable lineage entry recording a split or merge relationship between two lots.
 */
public record LotLineageEntry(
        String entryId,
        TrackedItemLotId lotId,
        TrackedItemLotId relatedLotId,
        LotRelationType relationType,
        int lotAmount,
        int relatedAmount,
        Instant occurredAt,
        String source,
        String idempotencyKey
) {

    public LotLineageEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(relatedLotId, "relatedLotId");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        source = source == null ? "" : source.strip();
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.strip();
        lotAmount = Math.max(0, lotAmount);
        relatedAmount = Math.max(0, relatedAmount);
    }
}
