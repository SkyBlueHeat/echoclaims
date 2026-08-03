package dev.worldecho.domain.item;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-Java, testable component that manages the pending-player set for
 * reconciliation coalescing.
 *
 * <p>Encapsulates the decision logic that was previously embedded inside the
 * Bukkit-dependent {@code PlayerInventoryReconciliationScheduler}.  The
 * scheduler delegates to this class so that coalescing, shutdown rejection,
 * and pending-state cleanup are all unit-testable without Bukkit.
 *
 * <p>Thread-safe: uses a concurrent set internally.
 */
public final class ReconciliationSchedulerState {

    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean shutdown;

    /**
     * Attempts to mark a player as pending reconciliation.
     *
     * @return {@code true} if the player was newly added (should schedule a task),
     *         {@code false} if the player was already pending or the scheduler is shut down
     */
    public boolean trySchedule(UUID playerUuid) {
        if (shutdown) {
            return false;
        }
        return pendingPlayers.add(playerUuid);
    }

    /**
     * Clears a player from the pending set after the reconciliation task has fired.
     */
    public void clear(UUID playerUuid) {
        pendingPlayers.remove(playerUuid);
    }

    /**
     * Rejects all future scheduling and clears pending state.
     */
    public void shutdown() {
        shutdown = true;
        pendingPlayers.clear();
    }

    public boolean isPending(UUID playerUuid) {
        return pendingPlayers.contains(playerUuid);
    }

    public int pendingCount() {
        return pendingPlayers.size();
    }

    public boolean isShutdown() {
        return shutdown;
    }
}
