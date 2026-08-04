package io.github.skyblueheat.echoclaims.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, per-player, expiring selection session that binds each displayed
 * index from {@code /ec claim claimable} to an exact incident UUID.
 *
 * <p>When a player runs {@code /ec claim claimable}, the resulting list of
 * claimable incidents is stored here. When the player subsequently runs
 * {@code /ec claim create <index>}, the index is resolved against the stored
 * session — never against a freshly recalculated list — so the index always
 * points to the same incident that was displayed.
 *
 * <p>Sessions expire after a configurable TTL. The total number of tracked
 * players is bounded. Restart clears all sessions because the cache is
 * in-memory only. All operations are thread-safe.
 */
public final class IncidentSelectionSession {

    private final Duration ttl;
    private final int maxPlayers;
    private final ConcurrentHashMap<UUID, Entry> sessions = new ConcurrentHashMap<>();

    public IncidentSelectionSession(Duration ttl, int maxPlayers) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.maxPlayers = Math.max(1, maxPlayers);
    }

    /**
     * Stores the claimable incident list for a player and returns the
     * indices that will be displayed.
     *
     * @param playerUuid the player
     * @param incidentIds ordered list of incident UUIDs matching the displayed list
     */
    public void store(UUID playerUuid, List<UUID> incidentIds) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(incidentIds, "incidentIds");
        cleanupExpired();
        if (sessions.size() >= maxPlayers && !sessions.containsKey(playerUuid)) {
            evictOldest();
        }
        sessions.put(playerUuid, new Entry(List.copyOf(incidentIds), System.currentTimeMillis()));
    }

    /**
     * Resolves a 1-based index to the incident UUID that was displayed at
     * that position in the player's current session.
     *
     * @param playerUuid the player
     * @param index 1-based index from the displayed list
     * @return the incident UUID, or empty if the session is missing, expired, or the index is out of range
     */
    public Optional<UUID> resolve(UUID playerUuid, int index) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (index < 1) {
            return Optional.empty();
        }
        Entry entry = sessions.get(playerUuid);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            sessions.remove(playerUuid);
            return Optional.empty();
        }
        if (index > entry.incidentIds.size()) {
            return Optional.empty();
        }
        return Optional.of(entry.incidentIds.get(index - 1));
    }

    /**
     * Clears the session for a player (e.g. after a successful claim creation).
     */
    public void clear(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    /**
     * Clears all sessions (called on shutdown).
     */
    public void clearAll() {
        sessions.clear();
    }

    /**
     * Returns the number of currently tracked players (for diagnostics).
     */
    public int trackedPlayerCount() {
        cleanupExpired();
        return sessions.size();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }

    private boolean isExpired(Entry entry) {
        return isExpired(entry, System.currentTimeMillis());
    }

    private boolean isExpired(Entry entry, long now) {
        return now - entry.createdAt > ttl.toMillis();
    }

    private void evictOldest() {
        UUID oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (var e : sessions.entrySet()) {
            if (e.getValue().createdAt < oldestTime) {
                oldestTime = e.getValue().createdAt;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            sessions.remove(oldestKey);
        }
    }

    private record Entry(List<UUID> incidentIds, long createdAt) {}
}
