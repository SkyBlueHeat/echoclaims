package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DeduplicationKeyFactoryTest {

    @Test
    void sameInputsProduceSameKey() {
        UUID player = UUID.randomUUID();
        long time = 10_000L;
        String world = "world";
        Coordinates coords = new Coordinates(1, 2, 3);

        String key1 = DeduplicationKeyFactory.forPlayerDeath(player, time, world, coords);
        String key2 = DeduplicationKeyFactory.forPlayerDeath(player, time, world, coords);

        assertEquals(key1, key2);
    }

    @Test
    void differentPlayersProduceDifferentKeys() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(p1, 1000L, "world", coords);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(p2, 1000L, "world", coords);

        assertNotEquals(k1, k2);
    }

    @Test
    void differentCoordinatesProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", new Coordinates(1, 2, 3));
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", new Coordinates(4, 5, 6));

        assertNotEquals(k1, k2);
    }

    @Test
    void sameSecondTimeBucketProducesSameKey() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1500L, "world", coords);

        assertEquals(k1, k2);
    }

    @Test
    void differentSecondTimeBucketsProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 2500L, "world", coords);

        assertNotEquals(k1, k2);
    }

    @Test
    void worldIsCaseInsensitive() {
        UUID player = UUID.randomUUID();
        Coordinates coords = new Coordinates(0, 0, 0);

        String k1 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "World", coords);
        String k2 = DeduplicationKeyFactory.forPlayerDeath(player, 1000L, "world", coords);

        assertEquals(k1, k2);
    }
}
