package io.github.skyblueheat.echoclaims.domain.claim;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit entry recording a single action on a claim.
 *
 * <p>Audit entries are append-only: they are never modified or deleted. Together
 * they form the complete history of a claim's lifecycle. Every state transition
 * must produce exactly one audit entry.</p>
 */
public record ClaimAuditEntry(
        UUID id,
        UUID claimId,
        ClaimActorType actorType,
        UUID actorUuid,
        ClaimAction action,
        String reason,
        long recordedAt,
        int claimVersion,
        Map<String, String> metadata
) {

    public ClaimAuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimId, "claimId");
        actorType = Objects.requireNonNull(actorType, "actorType");
        actorUuid = actorUuid;
        action = Objects.requireNonNull(action, "action");
        reason = reason == null ? "" : reason.strip();
        if (recordedAt < 0) {
            throw new IllegalArgumentException("recordedAt cannot be negative");
        }
        if (claimVersion < 0) {
            throw new IllegalArgumentException("claimVersion cannot be negative");
        }
        metadata = copyMetadata(metadata);
    }

    public boolean hasReason() {
        return !reason.isEmpty();
    }

    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }

    private static Map<String, String> copyMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            copy.put(entry.getKey().strip(), Objects.requireNonNullElse(entry.getValue(), "").strip());
        }
        return Map.copyOf(copy);
    }
}
