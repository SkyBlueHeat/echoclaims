package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates deterministic semantic deduplication keys for incidents.
 *
 * <p>The key is based on stable event facts — player UUID, incident type, a second-level
 * time bucket, world identifier, and death coordinates — not on random UUIDs or
 * {@code currentTimeMillis} alone. This ensures that two captures of the same death event
 * produce the same key, allowing the database uniqueness constraint to prevent
 * duplicates.</p>
 */
public final class DeduplicationKeyFactory {

    private static final long TIME_BUCKET_MILLIS = 1_000L;

    private DeduplicationKeyFactory() {
    }

    public static String forPlayerDeath(
            UUID playerUuid,
            long occurredAt,
            String worldId,
            Coordinates coordinates
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(coordinates, "coordinates");

        long timeBucket = occurredAt / TIME_BUCKET_MILLIS;

        return IncidentType.PLAYER_DEATH.name()
                + ":" + playerUuid
                + ":" + worldId.strip().toLowerCase(Locale.ROOT)
                + ":" + coordinates.x() + ":" + coordinates.y() + ":" + coordinates.z()
                + ":" + timeBucket;
    }
}
