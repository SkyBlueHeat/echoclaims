package dev.worldecho.application.memory;

import dev.worldecho.domain.content.IdentifiedContent;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything WorldEcho reads from a player death, captured on the server thread as
 * immutable data. No Bukkit object survives past the listener call.
 */
public record DeathCapture(
        Instant occurredAt,
        UUID worldId,
        String worldName,
        int x,
        int y,
        int z,
        UUID playerId,
        IdentifiedContent killer,
        UUID killerRuntimeId,
        LootCandidate loot,
        int inspectedDropCount
) {

    public DeathCapture {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(killer, "killer");
        worldName = worldName == null ? "" : worldName;
        inspectedDropCount = Math.max(0, inspectedDropCount);
    }

    public Optional<LootCandidate> optionalLoot() {
        return Optional.ofNullable(loot);
    }
}
