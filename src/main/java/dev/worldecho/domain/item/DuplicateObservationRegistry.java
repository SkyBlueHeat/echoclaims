package dev.worldecho.domain.item;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded in-memory registry for detecting duplicate UNIQUE identity observations.
 *
 * <p>Not a persistence truth source — only used for conflict diagnostics during
 * a single reconciliation cycle.  Stale observations expire safely.
 */
public final class DuplicateObservationRegistry {

    public record Observation(
            TrackedItemId identity,
            UUID playerUuid,
            String inventorySection,
            int slot,
            String contentFingerprint,
            long cycleSequence,
            long observedTimeMillis
    ) {
        public Observation {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(playerUuid, "playerUuid");
            inventorySection = inventorySection == null ? "" : inventorySection;
            contentFingerprint = contentFingerprint == null ? "" : contentFingerprint;
        }
    }

    public record DuplicateDiagnostic(
            TrackedItemId identity,
            Observation first,
            Observation second,
            boolean conflictingContent
    ) {}

    private final Map<TrackedItemId, Observation> observations = new ConcurrentHashMap<>();
    private final long expiryMillis;

    public DuplicateObservationRegistry(long expiryMillis) {
        this.expiryMillis = Math.max(1000, expiryMillis);
    }

    /**
     * Records an observation and returns a diagnostic if a duplicate is detected.
     */
    public DuplicateDiagnostic observe(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        expireStale();

        Observation existing = observations.putIfAbsent(observation.identity(), observation);
        if (existing != null) {
            boolean samePlayer = existing.playerUuid().equals(observation.playerUuid());
            boolean sameSlot = existing.slot() == observation.slot();
            if (samePlayer && sameSlot) {
                return null;
            }
            boolean conflicting = !existing.contentFingerprint().equals(observation.contentFingerprint());
            return new DuplicateDiagnostic(
                    observation.identity(),
                    existing,
                    observation,
                    conflicting
            );
        }
        return null;
    }

    public void clear() {
        observations.clear();
    }

    public int size() {
        return observations.size();
    }

    private void expireStale() {
        long now = System.currentTimeMillis();
        observations.entrySet().removeIf(entry ->
                now - entry.getValue().observedTimeMillis() > expiryMillis);
    }
}
