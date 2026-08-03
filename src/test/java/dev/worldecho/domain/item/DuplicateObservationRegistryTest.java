package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateObservationRegistryTest {

    @Test
    void firstObservationProducesNoDiagnostic() {
        DuplicateObservationRegistry registry = new DuplicateObservationRegistry(60_000);
        TrackedItemId id = TrackedItemId.random();
        DuplicateObservationRegistry.Observation obs = new DuplicateObservationRegistry.Observation(
                id, UUID.randomUUID(), "main", 0, "fp", 1, System.currentTimeMillis()
        );
        assertNull(registry.observe(obs));
    }

    @Test
    void samePlayerSameSlotNoDiagnostic() {
        DuplicateObservationRegistry registry = new DuplicateObservationRegistry(60_000);
        TrackedItemId id = TrackedItemId.random();
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        DuplicateObservationRegistry.Observation obs1 = new DuplicateObservationRegistry.Observation(
                id, player, "main", 0, "fp", 1, now);
        DuplicateObservationRegistry.Observation obs2 = new DuplicateObservationRegistry.Observation(
                id, player, "main", 0, "fp", 2, now + 1);
        registry.observe(obs1);
        assertNull(registry.observe(obs2));
    }

    @Test
    void differentPlayerSameIdentityProducesDiagnostic() {
        DuplicateObservationRegistry registry = new DuplicateObservationRegistry(60_000);
        TrackedItemId id = TrackedItemId.random();
        long now = System.currentTimeMillis();
        DuplicateObservationRegistry.Observation obs1 = new DuplicateObservationRegistry.Observation(
                id, UUID.randomUUID(), "main", 0, "fp", 1, now);
        DuplicateObservationRegistry.Observation obs2 = new DuplicateObservationRegistry.Observation(
                id, UUID.randomUUID(), "main", 1, "fp", 2, now + 1);
        registry.observe(obs1);
        DuplicateObservationRegistry.DuplicateDiagnostic result = registry.observe(obs2);
        assertNotNull(result);
        assertTrue(result.conflictingContent() == false);
    }

    @Test
    void conflictingContentFingerprintProducesDiagnostic() {
        DuplicateObservationRegistry registry = new DuplicateObservationRegistry(60_000);
        TrackedItemId id = TrackedItemId.random();
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        DuplicateObservationRegistry.Observation obs1 = new DuplicateObservationRegistry.Observation(
                id, player, "main", 0, "fp-a", 1, now);
        DuplicateObservationRegistry.Observation obs2 = new DuplicateObservationRegistry.Observation(
                id, player, "main", 1, "fp-b", 2, now + 1);
        registry.observe(obs1);
        DuplicateObservationRegistry.DuplicateDiagnostic result = registry.observe(obs2);
        assertNotNull(result);
        assertTrue(result.conflictingContent());
    }

    @Test
    void staleObservationsExpire() throws InterruptedException {
        DuplicateObservationRegistry registry = new DuplicateObservationRegistry(50);
        TrackedItemId id = TrackedItemId.random();
        DuplicateObservationRegistry.Observation obs = new DuplicateObservationRegistry.Observation(
                id, UUID.randomUUID(), "main", 0, "fp", 1, System.currentTimeMillis()
        );
        registry.observe(obs);
        assertEquals(1, registry.size());
        Thread.sleep(60);
        DuplicateObservationRegistry.Observation obs2 = new DuplicateObservationRegistry.Observation(
                id, UUID.randomUUID(), "main", 1, "fp", 2, System.currentTimeMillis()
        );
        registry.observe(obs2);
        assertEquals(1, registry.size());
    }
}
