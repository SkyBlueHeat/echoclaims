package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DeduplicationKeyFactoryTest {

    private static final String FINGERPRINT = "abc123def456";
    private static final String CAUSE = "FALL";

    @Test
    void sameInputsProduceSameKey() {
        UUID player = UUID.randomUUID();
        long time = 10_000L;
        String world = "world";
        Coordinates coords = new Coordinates(1, 2, 3);

        String key1 = DeduplicationKeyFactory.forPlayerDeath(player, time, world, coords, CAUSE, FINGERPRINT);
        String key2 = DeduplicationKeyFactory.forPlayerDeath(player, time, world, coords, CAUSE, FINGERPRINT);

        assertEquals(key1, key2);
    }

    @Test
    void differentPlayersProduceDifferentKeys() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(p1, 1000L, "world", coords, CAUSE, FINGERPRINT);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(p2, 1000L, "world", coords, CAUSE, FINGERPRINT);

        assertNotEquals(k1, k2);
    }

    @Test
    void differentCoordinatesProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", new Coordinates(1, 2, 3), CAUSE, FINGERPRINT);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", new Coordinates(4, 5, 6), CAUSE, FINGERPRINT);

        assertNotEquals(k1, k2);
    }

    @Test
    void sameSecondTimeBucketProducesSameKey() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords, CAUSE, FINGERPRINT);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1500L, "world", coords, CAUSE, FINGERPRINT);

        assertEquals(k1, k2);
    }

    @Test
    void differentSecondTimeBucketsProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords, CAUSE, FINGERPRINT);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 2500L, "world", coords, CAUSE, FINGERPRINT);

        assertNotEquals(k1, k2);
    }

    @Test
    void worldIsCaseInsensitive() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "World", coords, CAUSE, FINGERPRINT);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords, CAUSE, FINGERPRINT);

        assertEquals(k1, k2);
    }

    @Test
    void differentDeathCausesProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords, "FALL", FINGERPRINT);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords, "ENTITY_ATTACK", FINGERPRINT);

        assertNotEquals(k1, k2);
    }

    @Test
    void differentSnapshotFingerprintsProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords, CAUSE, "fp1");
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords, CAUSE, "fp2");

        assertNotEquals(k1, k2);
    }

    @Test
    void twoDeathsSameSecondSameLocationDifferentInventoryProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(10, 20, 30);
        long sameSecond = 5_000L;

        InventorySnapshot snapshot1 = new InventorySnapshot(
                UUID.randomUUID(), player, CaptureReason.PRE_DEATH, sameSecond,
                "world", coords, "survival", 20, 20, 0, 0,
                List.of(new SnapshotItem("0", "minecraft:diamond_sword", 1, "data", "", 42, "Excalibur", 10, 1561, Map.of())),
                List.of(), null, null, 1
        );
        InventorySnapshot snapshot2 = new InventorySnapshot(
                UUID.randomUUID(), player, CaptureReason.PRE_DEATH, sameSecond,
                "world", coords, "survival", 20, 20, 0, 0,
                List.of(new SnapshotItem("0", "minecraft:netherite_sword", 1, "data2", "", 50, "", 0, 2031, Map.of())),
                List.of(), null, null, 1
        );

        String fp1 = DeduplicationKeyFactory.snapshotFingerprint(snapshot1);
        String fp2 = DeduplicationKeyFactory.snapshotFingerprint(snapshot2);

        assertNotEquals(fp1, fp2);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, sameSecond, "world", coords, "ENTITY_ATTACK", fp1);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, sameSecond, "world", coords, "ENTITY_ATTACK", fp2);

        assertNotEquals(k1, k2);
    }

    @Test
    void sameSnapshotProducesSameFingerprint() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(10, 20, 30);

        InventorySnapshot snapshot1 = new InventorySnapshot(
                UUID.randomUUID(), player, CaptureReason.PRE_DEATH, 5_000L,
                "world", coords, "survival", 20, 20, 0, 0,
                List.of(new SnapshotItem("0", "minecraft:stone", 32, "data", "", 5, "", 0, 0, Map.of())),
                List.of(), null, null, 1
        );
        InventorySnapshot snapshot2 = new InventorySnapshot(
                UUID.randomUUID(), player, CaptureReason.PRE_DEATH, 6_000L,
                "world", coords, "survival", 20, 20, 0, 0,
                List.of(new SnapshotItem("0", "minecraft:stone", 32, "data", "", 5, "", 0, 0, Map.of())),
                List.of(), null, null, 1
        );

        assertEquals(
                DeduplicationKeyFactory.snapshotFingerprint(snapshot1),
                DeduplicationKeyFactory.snapshotFingerprint(snapshot2)
        );
    }

    @Test
    void emptySnapshotProducesConsistentFingerprint() {
        UUID player = UUID.randomUUID();
        InventorySnapshot empty = new InventorySnapshot(
                UUID.randomUUID(), player, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        );

        String fp1 = DeduplicationKeyFactory.snapshotFingerprint(empty);
        String fp2 = DeduplicationKeyFactory.snapshotFingerprint(empty);
        assertEquals(fp1, fp2);
    }

    @Test
    void differentAmountsProduceDifferentFingerprints() {
        UUID player = UUID.randomUUID();
        InventorySnapshot s1 = new InventorySnapshot(
                UUID.randomUUID(), player, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0,
                List.of(new SnapshotItem("0", "minecraft:stone", 32, "data", "", 5, "", 0, 0, Map.of())),
                List.of(), null, null, 1
        );
        InventorySnapshot s2 = new InventorySnapshot(
                UUID.randomUUID(), player, CaptureReason.PRE_DEATH, 1000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0,
                List.of(new SnapshotItem("0", "minecraft:stone", 64, "data", "", 5, "", 0, 0, Map.of())),
                List.of(), null, null, 1
        );

        assertNotEquals(
                DeduplicationKeyFactory.snapshotFingerprint(s1),
                DeduplicationKeyFactory.snapshotFingerprint(s2)
        );
    }
}
