package dev.worldecho.domain.memory;

import dev.worldecho.domain.content.ContentKey;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StoryMemoryEvent(
        UUID id,
        MemoryEventType type,
        Instant occurredAt,
        UUID worldId,
        int x,
        int y,
        int z,
        UUID playerId,
        ContentKey actor,
        String actorRuntimeId,
        ContentKey item,
        String itemSnapshot,
        String details
) {

    public StoryMemoryEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(actor, "actor");
        actorRuntimeId = Objects.requireNonNullElse(actorRuntimeId, "");
        itemSnapshot = Objects.requireNonNullElse(itemSnapshot, "");
        details = Objects.requireNonNullElse(details, "");
    }

    public Optional<ContentKey> optionalItem() {
        return Optional.ofNullable(item);
    }
}
