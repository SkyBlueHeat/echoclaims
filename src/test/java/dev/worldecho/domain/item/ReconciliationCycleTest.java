package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationCycleTest {

    @Test
    void idempotencyKeyForItemIsDeterministic() {
        UUID playerUuid = UUID.randomUUID();
        ReconciliationCycle cycle = ReconciliationCycle.create(
                playerUuid, "join", 1, "server-1", 100L);
        TrackedItemId itemId = TrackedItemId.random();

        String key1 = cycle.idempotencyKeyFor(itemId);
        String key2 = cycle.idempotencyKeyFor(itemId);
        assertEquals(key1, key2);
        assertTrue(key1.contains("item:"));
        assertTrue(key1.contains(itemId.toString()));
    }

    @Test
    void idempotencyKeyForLotIsDeterministic() {
        UUID playerUuid = UUID.randomUUID();
        ReconciliationCycle cycle = ReconciliationCycle.create(
                playerUuid, "pickup", 5, "server-1", 200L);
        TrackedItemLotId lotId = TrackedItemLotId.random();

        String key1 = cycle.idempotencyKeyFor(lotId);
        String key2 = cycle.idempotencyKeyFor(lotId);
        assertEquals(key1, key2);
        assertTrue(key1.contains("lot:"));
        assertTrue(key1.contains(lotId.toString()));
    }

    @Test
    void differentCyclesProduceDifferentKeys() {
        UUID playerUuid = UUID.randomUUID();
        ReconciliationCycle cycle1 = ReconciliationCycle.create(
                playerUuid, "join", 1, "server-1", 100L);
        ReconciliationCycle cycle2 = ReconciliationCycle.create(
                playerUuid, "join", 2, "server-1", 200L);
        TrackedItemId itemId = TrackedItemId.random();

        assertNotEquals(cycle1.idempotencyKeyFor(itemId), cycle2.idempotencyKeyFor(itemId));
    }

    @Test
    void differentPlayersProduceDifferentKeys() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        ReconciliationCycle cycle1 = ReconciliationCycle.create(
                player1, "join", 1, "server-1", 100L);
        ReconciliationCycle cycle2 = ReconciliationCycle.create(
                player2, "join", 1, "server-1", 100L);
        TrackedItemId itemId = TrackedItemId.random();

        assertNotEquals(cycle1.idempotencyKeyFor(itemId), cycle2.idempotencyKeyFor(itemId));
    }
}
