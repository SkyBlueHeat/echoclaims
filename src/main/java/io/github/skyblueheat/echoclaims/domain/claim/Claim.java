package io.github.skyblueheat.echoclaims.domain.claim;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a player's request for item restoration based on recorded evidence.
 *
 * <p>A claim is linked to exactly one {@link io.github.skyblueheat.echoclaims.domain.incident.Incident}.
 * The {@code publicReference} is a short, human-readable identifier that is unique across all
 * claims. The {@code version} field supports optimistic concurrency: every update increments
 * the version, and a stale version on update causes the update to fail.</p>
 *
 * <p>Instances are created on the server thread and safe to hand to asynchronous
 * persistence because they retain no mutable Bukkit state.</p>
 */
public record Claim(
        UUID id,
        String publicReference,
        UUID incidentId,
        UUID playerUuid,
        ClaimStatus status,
        ClaimSource source,
        String description,
        long createdAt,
        long submittedAt,
        long cancelledAt,
        int version,
        Map<String, String> metadata
) {

    public Claim {
        Objects.requireNonNull(id, "id");
        publicReference = Objects.requireNonNull(publicReference, "publicReference").strip();
        if (publicReference.isEmpty()) {
            throw new IllegalArgumentException("publicReference cannot be blank");
        }
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        status = Objects.requireNonNull(status, "status");
        source = Objects.requireNonNull(source, "source");
        description = description == null ? "" : description.strip();
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt cannot be negative");
        }
        if (submittedAt < 0) {
            throw new IllegalArgumentException("submittedAt cannot be negative");
        }
        if (cancelledAt < 0) {
            throw new IllegalArgumentException("cancelledAt cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        metadata = copyMetadata(metadata);
    }

    public boolean isDraft() {
        return status == ClaimStatus.DRAFT;
    }

    public boolean isSubmitted() {
        return status == ClaimStatus.SUBMITTED;
    }

    public boolean isCancelled() {
        return status == ClaimStatus.CANCELLED;
    }

    public boolean isOpen() {
        return status == ClaimStatus.DRAFT || status == ClaimStatus.SUBMITTED;
    }

    public boolean hasDescription() {
        return !description.isEmpty();
    }

    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }

    public String metadataValue(String key) {
        Objects.requireNonNull(key, "key");
        return metadata.get(key.strip());
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
