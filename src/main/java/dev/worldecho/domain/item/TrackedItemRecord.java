package dev.worldecho.domain.item;

import dev.worldecho.domain.content.ContentKey;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable persistent record for a WorldEcho-tracked item.
 *
 * <p>Initial snapshot fields (created_at, first_seen_at, content_key, provider_id,
 * initial_material, initial_custom_name, initial_value_score, tracking_reason,
 * created_by_subject) are immutable after creation.  Only last_seen_at may be updated
 * through an explicit observation operation.
 */
public record TrackedItemRecord(
        TrackedItemId itemId,
        Instant createdAt,
        Instant firstSeenAt,
        Instant lastSeenAt,
        ContentKey contentKey,
        String providerId,
        String initialMaterial,
        String initialCustomName,
        Integer initialValueScore,
        String trackingReason,
        String createdBySubject
) {

    public TrackedItemRecord {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        Objects.requireNonNull(contentKey, "contentKey");
        providerId = providerId == null ? "" : providerId.strip().toLowerCase(java.util.Locale.ROOT);
        initialMaterial = initialMaterial == null ? "" : initialMaterial.strip();
        initialCustomName = initialCustomName == null ? "" : initialCustomName.strip();
        trackingReason = trackingReason == null ? "" : trackingReason.strip();
        createdBySubject = createdBySubject == null ? "" : createdBySubject.strip();
    }

    public Optional<String> optionalInitialCustomName() {
        return initialCustomName.isEmpty() ? Optional.empty() : Optional.of(initialCustomName);
    }

    public Optional<Integer> optionalInitialValueScore() {
        return Optional.ofNullable(initialValueScore);
    }

    public Optional<String> optionalCreatedBySubject() {
        return createdBySubject.isEmpty() ? Optional.empty() : Optional.of(createdBySubject);
    }

    public TrackedItemRecord withLastSeenAt(Instant newLastSeenAt) {
        return new TrackedItemRecord(
                itemId, createdAt, firstSeenAt, newLastSeenAt,
                contentKey, providerId, initialMaterial, initialCustomName,
                initialValueScore, trackingReason, createdBySubject
        );
    }
}
