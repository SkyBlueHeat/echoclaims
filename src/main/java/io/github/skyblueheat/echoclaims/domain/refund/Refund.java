package io.github.skyblueheat.echoclaims.domain.refund;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a refund derived from a finalized staff review.
 *
 * <p>A refund is created only when a review is finalized with outcome
 * {@code APPROVED} or {@code PARTIALLY_APPROVED}. It references the claim,
 * review, and player. The {@code version} field supports optimistic
 * concurrency: every state transition increments the version, and a stale
 * version on update causes the update to fail.</p>
 *
 * <p>There is at most one refund per finalized review. This is enforced by a
 * unique constraint on {@code review_id} in the database, providing
 * database-level idempotency: creating a refund for the same review twice
 * is rejected by the constraint, not by in-memory logic alone.</p>
 *
 * <p>Instances are created on the server thread and safe to hand to
 * asynchronous persistence because they retain no mutable Bukkit state.</p>
 */
public record Refund(
        UUID id,
        UUID claimId,
        UUID reviewId,
        UUID playerUuid,
        RefundStatus status,
        long createdAt,
        long completedAt,
        int version,
        Map<String, String> metadata
) {

    public Refund {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        status = Objects.requireNonNull(status, "status");
        if (status == RefundStatus.COMPLETED) {
            if (completedAt <= 0) {
                throw new IllegalArgumentException("completedAt must be positive for completed refund");
            }
        } else {
            if (completedAt != 0) {
                throw new IllegalArgumentException("completedAt must be 0 for non-completed refund");
            }
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt cannot be negative");
        }
        if (completedAt < 0) {
            throw new IllegalArgumentException("completedAt cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        metadata = copyMetadata(metadata);
    }

    public boolean isPending() {
        return status == RefundStatus.PENDING;
    }

    public boolean isReady() {
        return status == RefundStatus.READY;
    }

    public boolean isDelivering() {
        return status == RefundStatus.DELIVERING;
    }

    public boolean isCompleted() {
        return status == RefundStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == RefundStatus.FAILED;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean isDeliverable() {
        return status.isDeliverable();
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
