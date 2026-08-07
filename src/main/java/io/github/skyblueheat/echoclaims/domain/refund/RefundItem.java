package io.github.skyblueheat.echoclaims.domain.refund;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a single refundable item within a {@link Refund}.
 *
 * <p>Each refund item is derived from a finalized
 * {@link io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision}
 * with outcome {@code APPROVED} or {@code PARTIALLY_APPROVED}. The
 * {@code refundableQuantity} is the total quantity that must be delivered to
 * the player. The {@code deliveredQuantity} tracks how much has been
 * successfully delivered so far.</p>
 *
 * <p>The {@code serializedItemData} is the original captured evidence payload
 * from the inventory snapshot. It is copied at refund creation time so that
 * later changes to historical evidence cannot affect the refund. The
 * {@code evidenceItemReference} and {@code sourceSnapshotId} provide
 * traceability back to the original evidence.</p>
 *
 * <p>Once a refund is created, the approved restoration set is immutable:
 * the {@code refundableQuantity}, {@code serializedItemData},
 * {@code evidenceItemReference}, and {@code sourceSnapshotId} fields never
 * change. Only {@code deliveredQuantity}, {@code status}, {@code failureReason},
 * {@code updatedAt}, and {@code version} may change during delivery.</p>
 *
 * <p>The {@code version} field supports optimistic concurrency for delivery
 * updates.</p>
 */
public record RefundItem(
        UUID id,
        UUID refundId,
        UUID claimId,
        UUID reviewId,
        UUID playerUuid,
        String evidenceItemReference,
        UUID sourceSnapshotId,
        String materialKey,
        int refundableQuantity,
        int deliveredQuantity,
        String serializedItemData,
        RefundItemStatus status,
        String failureReason,
        long createdAt,
        long updatedAt,
        int version
) {

    public RefundItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        evidenceItemReference = Objects.requireNonNull(evidenceItemReference, "evidenceItemReference").strip();
        if (evidenceItemReference.isEmpty()) {
            throw new IllegalArgumentException("evidenceItemReference cannot be blank");
        }
        Objects.requireNonNull(sourceSnapshotId, "sourceSnapshotId");
        materialKey = Objects.requireNonNull(materialKey, "materialKey").strip();
        if (materialKey.isEmpty()) {
            throw new IllegalArgumentException("materialKey cannot be blank");
        }
        if (refundableQuantity <= 0) {
            throw new IllegalArgumentException("refundableQuantity must be positive");
        }
        if (deliveredQuantity < 0) {
            throw new IllegalArgumentException("deliveredQuantity cannot be negative");
        }
        if (deliveredQuantity > refundableQuantity) {
            throw new IllegalArgumentException("deliveredQuantity cannot exceed refundableQuantity");
        }
        status = Objects.requireNonNull(status, "status");
        serializedItemData = Objects.requireNonNullElse(serializedItemData, "");
        failureReason = failureReason == null ? "" : failureReason.strip();
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt cannot be negative");
        }
        if (updatedAt < 0) {
            throw new IllegalArgumentException("updatedAt cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        validateStatusConsistency(status, deliveredQuantity, refundableQuantity);
    }

    private static void validateStatusConsistency(RefundItemStatus status, int delivered, int refundable) {
        switch (status) {
            case PENDING -> {
                if (delivered != 0) {
                    throw new IllegalArgumentException("PENDING requires deliveredQuantity == 0");
                }
            }
            case PARTIALLY_DELIVERED -> {
                if (delivered <= 0 || delivered >= refundable) {
                    throw new IllegalArgumentException(
                            "PARTIALLY_DELIVERED requires 0 < deliveredQuantity < refundableQuantity");
                }
            }
            case DELIVERED -> {
                if (delivered != refundable) {
                    throw new IllegalArgumentException("DELIVERED requires deliveredQuantity == refundableQuantity");
                }
            }
            case FAILED -> { }
        }
    }

    public boolean isPending() {
        return status.isPending();
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean isFullyDelivered() {
        return status == RefundItemStatus.DELIVERED;
    }

    public int remainingQuantity() {
        return refundableQuantity - deliveredQuantity;
    }

    public boolean hasFailureReason() {
        return !failureReason.isEmpty();
    }

    public boolean hasSerializedData() {
        return !serializedItemData.isEmpty();
    }
}
