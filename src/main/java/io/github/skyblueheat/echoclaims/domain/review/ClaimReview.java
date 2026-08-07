package io.github.skyblueheat.echoclaims.domain.review;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a staff review for a claim.
 *
 * <p>A claim has no more than one active review. The review tracks the
 * assigned reviewer, internal state, final outcome, and timestamps. The
 * {@code version} field supports optimistic concurrency: every update
 * increments the version, and a stale version on update causes the update
 * to fail.</p>
 *
 * <p>Instances are created on the server thread and safe to hand to
 * asynchronous persistence because they retain no mutable Bukkit state.</p>
 */
public record ClaimReview(
        UUID id,
        UUID claimId,
        UUID assignedReviewerUuid,
        ReviewState reviewState,
        ReviewOutcome finalOutcome,
        String finalSummary,
        long startedAt,
        long updatedAt,
        long finalizedAt,
        int version,
        Map<String, String> metadata
) {

    public ClaimReview {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(assignedReviewerUuid, "assignedReviewerUuid");
        reviewState = Objects.requireNonNull(reviewState, "reviewState");
        if (reviewState == ReviewState.FINALIZED) {
            Objects.requireNonNull(finalOutcome, "finalOutcome");
            if (finalizedAt <= 0) {
                throw new IllegalArgumentException("finalizedAt must be positive for finalized review");
            }
        } else {
            if (finalOutcome != null) {
                throw new IllegalArgumentException("finalOutcome must be null for open review");
            }
            if (finalizedAt != 0) {
                throw new IllegalArgumentException("finalizedAt must be 0 for open review");
            }
        }
        finalSummary = finalSummary == null ? "" : finalSummary.strip();
        if (startedAt < 0) {
            throw new IllegalArgumentException("startedAt cannot be negative");
        }
        if (updatedAt < 0) {
            throw new IllegalArgumentException("updatedAt cannot be negative");
        }
        if (finalizedAt < 0) {
            throw new IllegalArgumentException("finalizedAt cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        metadata = copyMetadata(metadata);
    }

    public boolean isOpen() {
        return reviewState == ReviewState.OPEN;
    }

    public boolean isFinalized() {
        return reviewState == ReviewState.FINALIZED;
    }

    public boolean hasFinalSummary() {
        return !finalSummary.isEmpty();
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
