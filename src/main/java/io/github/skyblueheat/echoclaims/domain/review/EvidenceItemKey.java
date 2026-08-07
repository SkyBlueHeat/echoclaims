package io.github.skyblueheat.echoclaims.domain.review;

import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;

import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic, immutable evidence-item key derived from stable fields.
 *
 * <p>The key is composed of the snapshot UUID and the slot identifier,
 * separated by a colon. Both fields are immutable in the existing schema
 * (enforced by the {@code UNIQUE(snapshot_uuid, slot)} constraint), so the
 * key remains stable after restart and is never recalculated from a new
 * list.</p>
 *
 * <p>The serialized evidence payload is never modified or rewritten.</p>
 */
public final class EvidenceItemKey {

    private EvidenceItemKey() {
    }

    /**
     * Builds a stable evidence-item reference from a snapshot UUID and slot.
     *
     * @param snapshotUuid the snapshot UUID
     * @param slot the slot identifier within the snapshot
     * @return a deterministic key string
     */
    public static String of(UUID snapshotUuid, String slot) {
        Objects.requireNonNull(snapshotUuid, "snapshotUuid");
        Objects.requireNonNull(slot, "slot");
        return snapshotUuid.toString() + ":" + slot.strip();
    }

    /**
     * Builds a stable evidence-item reference from a snapshot UUID and a
     * {@link SnapshotItem}.
     *
     * @param snapshotUuid the snapshot UUID
     * @param item the snapshot item
     * @return a deterministic key string
     */
    public static String of(UUID snapshotUuid, SnapshotItem item) {
        Objects.requireNonNull(item, "item");
        return of(snapshotUuid, item.slot());
    }

    /**
     * Extracts the snapshot UUID from a stable evidence-item reference.
     *
     * @param reference the evidence-item reference
     * @return the snapshot UUID
     */
    public static UUID snapshotUuid(String reference) {
        Objects.requireNonNull(reference, "reference");
        int separator = reference.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Invalid evidence-item reference: " + reference);
        }
        return UUID.fromString(reference.substring(0, separator));
    }

    /**
     * Extracts the slot from a stable evidence-item reference.
     *
     * @param reference the evidence-item reference
     * @return the slot identifier
     */
    public static String slot(String reference) {
        Objects.requireNonNull(reference, "reference");
        int separator = reference.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Invalid evidence-item reference: " + reference);
        }
        return reference.substring(separator + 1);
    }
}
