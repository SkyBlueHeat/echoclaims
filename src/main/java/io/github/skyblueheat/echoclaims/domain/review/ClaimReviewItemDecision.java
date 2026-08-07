package io.github.skyblueheat.echoclaims.domain.review;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a staff decision on a single evidence item.
 *
 * <p>The {@code evidenceItemReference} is a stable, deterministic key derived
 * from the snapshot UUID and slot (e.g. {@code "snapshot-uuid:slot"}). It
 * remains stable after restart and is never recalculated from a new list.</p>
 *
 * <p>Validation rules enforced by {@link ReviewItemOutcome}:</p>
 * <ul>
 *   <li>{@code APPROVED} requires approvedQuantity == originalQuantity</li>
 *   <li>{@code PARTIALLY_APPROVED} requires 0 < approvedQuantity < originalQuantity</li>
 *   <li>{@code REJECTED} requires approvedQuantity == 0</li>
 *   <li>approvedQuantity may never be negative or exceed originalQuantity</li>
 * </ul>
 *
 * <p>The {@code version} field supports optimistic concurrency for updates.</p>
 */
public record ClaimReviewItemDecision(
        UUID id,
        UUID reviewId,
        UUID claimId,
        String evidenceItemReference,
        UUID sourceSnapshotId,
        int originalQuantity,
        int approvedQuantity,
        ReviewItemOutcome outcome,
        ReviewReasonCode reasonCode,
        String staffNote,
        long createdAt,
        long updatedAt,
        int version,
        Map<String, String> metadata
) {

    public ClaimReviewItemDecision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(claimId, "claimId");
        evidenceItemReference = Objects.requireNonNull(evidenceItemReference, "evidenceItemReference").strip();
        if (evidenceItemReference.isEmpty()) {
            throw new IllegalArgumentException("evidenceItemReference cannot be blank");
        }
        Objects.requireNonNull(sourceSnapshotId, "sourceSnapshotId");
        outcome = Objects.requireNonNull(outcome, "outcome");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        staffNote = staffNote == null ? "" : staffNote.strip();
        if (originalQuantity < 0) {
            throw new IllegalArgumentException("originalQuantity cannot be negative");
        }
        if (approvedQuantity < 0) {
            throw new IllegalArgumentException("approvedQuantity cannot be negative");
        }
        if (approvedQuantity > originalQuantity) {
            throw new IllegalArgumentException("approvedQuantity cannot exceed originalQuantity");
        }
        validateOutcomeQuantity(outcome, approvedQuantity, originalQuantity);
        if (reasonCode == ReviewReasonCode.OTHER && staffNote.isEmpty()) {
            throw new IllegalArgumentException(
                    "OTHER reason code requires a non-blank staffNote");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt cannot be negative");
        }
        if (updatedAt < 0) {
            throw new IllegalArgumentException("updatedAt cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        metadata = copyMetadata(metadata);
    }

    private static void validateOutcomeQuantity(ReviewItemOutcome outcome, int approved, int original) {
        switch (outcome) {
            case APPROVED -> {
                if (approved != original) {
                    throw new IllegalArgumentException(
                            "APPROVED requires approvedQuantity == originalQuantity");
                }
            }
            case PARTIALLY_APPROVED -> {
                if (approved <= 0 || approved >= original) {
                    throw new IllegalArgumentException(
                            "PARTIALLY_APPROVED requires 0 < approvedQuantity < originalQuantity");
                }
            }
            case REJECTED -> {
                if (approved != 0) {
                    throw new IllegalArgumentException(
                            "REJECTED requires approvedQuantity == 0");
                }
            }
        }
    }

    public boolean hasStaffNote() {
        return !staffNote.isEmpty();
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
