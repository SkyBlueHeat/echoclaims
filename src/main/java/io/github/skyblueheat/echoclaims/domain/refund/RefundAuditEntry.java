package io.github.skyblueheat.echoclaims.domain.refund;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit entry recording a single action on a refund.
 *
 * <p>Audit entries are append-only: they are never modified or deleted. Together
 * they form the complete history of a refund's lifecycle. Every state transition
 * and delivery attempt must produce exactly one audit entry.</p>
 *
 * <p>Each entry captures enough information to determine:</p>
 * <ul>
 *   <li>actor — who or what initiated the action</li>
 *   <li>player — which player the refund belongs to</li>
 *   <li>claim — which claim the refund is derived from</li>
 *   <li>review — which finalized review approved the refund</li>
 *   <li>refund — which refund was affected</li>
 *   <li>item — which specific refund item (may be null for refund-level events)</li>
 *   <li>quantity — how many items were delivered (may be 0 for non-delivery events)</li>
 *   <li>timestamp — when the action occurred</li>
 *   <li>reason/failure — diagnostic information for failures</li>
 * </ul>
 */
public record RefundAuditEntry(
        UUID id,
        UUID refundId,
        UUID claimId,
        UUID reviewId,
        UUID playerUuid,
        UUID actorUuid,
        RefundAction action,
        UUID refundItemId,
        int quantity,
        String reason,
        long recordedAt,
        Map<String, String> metadata
) {

    public RefundAuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        action = Objects.requireNonNull(action, "action");
        refundItemId = refundItemId;
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        reason = reason == null ? "" : reason.strip();
        if (recordedAt < 0) {
            throw new IllegalArgumentException("recordedAt cannot be negative");
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
