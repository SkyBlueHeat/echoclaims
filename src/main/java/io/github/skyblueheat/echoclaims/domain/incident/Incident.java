package io.github.skyblueheat.echoclaims.domain.incident;

import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a discrete event that may produce a lost-item claim.
 *
 * <p>Every incident is linked to at least one pre-event inventory snapshot. A post-event
 * snapshot may be present when it was safely captured. The {@code deduplicationKey} is a
 * deterministic semantic key that prevents duplicate incidents for the same event.</p>
 *
 * <p>Instances are created on the server thread and safe to hand to asynchronous
 * persistence because they retain no mutable Bukkit state.</p>
 */
public record Incident(
        UUID id,
        IncidentType type,
        UUID playerUuid,
        long occurredAt,
        String worldId,
        Coordinates coordinates,
        String cause,
        UUID killerPlayerUuid,
        String killerEntityKey,
        UUID preEventSnapshotUuid,
        UUID postEventSnapshotUuid,
        IncidentStatus status,
        Map<String, String> metadata,
        String deduplicationKey
) {

    public Incident {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (occurredAt < 0) {
            throw new IllegalArgumentException("occurredAt cannot be negative");
        }
        worldId = Objects.requireNonNull(worldId, "worldId").strip();
        if (worldId.isEmpty()) {
            throw new IllegalArgumentException("worldId cannot be blank");
        }
        cause = Objects.requireNonNullElse(cause, "").strip();
        killerEntityKey = killerEntityKey == null ? "" : killerEntityKey.strip().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(preEventSnapshotUuid, "preEventSnapshotUuid");
        status = Objects.requireNonNullElse(status, IncidentStatus.OPEN);
        metadata = copyMetadata(metadata);
        deduplicationKey = Objects.requireNonNull(deduplicationKey, "deduplicationKey").strip();
        if (deduplicationKey.isEmpty()) {
            throw new IllegalArgumentException("deduplicationKey cannot be blank");
        }
    }

    public boolean hasCoordinates() {
        return coordinates != null;
    }

    public boolean hasKillerPlayer() {
        return killerPlayerUuid != null;
    }

    public boolean hasKillerEntity() {
        return !killerEntityKey.isEmpty();
    }

    public boolean hasPostEventSnapshot() {
        return postEventSnapshotUuid != null;
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
