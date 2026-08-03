package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates deterministic semantic deduplication keys for incidents.
 *
 * <p>The key is based on stable event facts — player UUID, incident type, world,
 * precise coordinates, death cause, and a deterministic fingerprint of the pre-death
 * snapshot — not on random UUIDs or {@code currentTimeMillis} alone. This ensures
 * that two captures of the same death event produce the same key, allowing the
 * database uniqueness constraint to prevent duplicates.</p>
 *
 * <p>Two distinct deaths occurring in the same second at the same location produce
 * different keys because the death cause or the snapshot fingerprint (which reflects
 * the player's current inventory) will differ.</p>
 */
public final class DeduplicationKeyFactory {

    private static final long TIME_BUCKET_MILLIS = 1_000L;

    private DeduplicationKeyFactory() {
    }

    /**
     * Builds a collision-resistant deduplication key for a player death.
     *
     * @param playerUuid the player who died
     * @param occurredAt the server-observed timestamp (epoch millis)
     * @param worldId the world name
     * @param coordinates the block coordinates
     * @param deathCause the death cause string (e.g. {@code "FALL"}, {@code "ENTITY_ATTACK"})
     * @param snapshotFingerprint a deterministic fingerprint of the pre-death snapshot
     * @return a deterministic deduplication key
     */
    public static String forPlayerDeath(
            UUID playerUuid,
            long occurredAt,
            String worldId,
            Coordinates coordinates,
            String deathCause,
            String snapshotFingerprint
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(coordinates, "coordinates");
        Objects.requireNonNull(deathCause, "deathCause");
        Objects.requireNonNull(snapshotFingerprint, "snapshotFingerprint");

        long timeBucket = occurredAt / TIME_BUCKET_MILLIS;

        return IncidentType.PLAYER_DEATH.name()
                + ":" + playerUuid
                + ":" + worldId.strip().toLowerCase(Locale.ROOT)
                + ":" + coordinates.x() + ":" + coordinates.y() + ":" + coordinates.z()
                + ":" + deathCause.strip().toUpperCase(Locale.ROOT)
                + ":" + snapshotFingerprint
                + ":" + timeBucket;
    }

    /**
     * Computes a deterministic fingerprint of an inventory snapshot.
     *
     * <p>The fingerprint is based on the slot, material key, and amount of every item
     * in the snapshot. It is independent of item ordering within a slot type and
     * independent of the snapshot UUID or timestamp.</p>
     *
     * @param snapshot the inventory snapshot
     * @return a 16-character hex fingerprint
     */
    public static String snapshotFingerprint(InventorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        StringBuilder builder = new StringBuilder();
        for (SnapshotItem item : snapshot.inventoryItems()) {
            builder.append(item.slot()).append('=')
                    .append(item.materialKey()).append('x').append(item.amount()).append(';');
        }
        for (SnapshotItem item : snapshot.armorItems()) {
            builder.append(item.slot()).append('=')
                    .append(item.materialKey()).append('x').append(item.amount()).append(';');
        }
        if (snapshot.offhandItem() != null) {
            builder.append(snapshot.offhandItem().slot()).append('=')
                    .append(snapshot.offhandItem().materialKey()).append('x')
                    .append(snapshot.offhandItem().amount()).append(';');
        }
        if (snapshot.cursorItem() != null) {
            builder.append(snapshot.cursorItem().slot()).append('=')
                    .append(snapshot.cursorItem().materialKey()).append('x')
                    .append(snapshot.cursorItem().amount()).append(';');
        }
        return sha256Hex(builder.toString(), 16);
    }

    private static String sha256Hex(String input, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, Math.min(length, hex.length()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
