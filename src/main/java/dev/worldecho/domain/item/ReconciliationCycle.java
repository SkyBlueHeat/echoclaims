package dev.worldecho.domain.item;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier for a single reconciliation cycle.
 *
 * <p>Used for deterministic idempotency keys.  Not exposed to players.
 */
public record ReconciliationCycle(
        UUID cycleId,
        UUID playerUuid,
        String triggerReason,
        long cycleSequence,
        String serverSessionId,
        long scheduledTick
) {

    public ReconciliationCycle {
        Objects.requireNonNull(cycleId, "cycleId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        triggerReason = triggerReason == null ? "" : triggerReason.strip();
        serverSessionId = serverSessionId == null ? "" : serverSessionId.strip();
    }

    /**
     * Deterministic idempotency key for a specific item or lot within this cycle.
     */
    public String idempotencyKeyFor(TrackedItemId itemId) {
        return "recon:" + serverSessionId + ":" + playerUuid + ":"
                + cycleSequence + ":item:" + itemId;
    }

    public String idempotencyKeyFor(TrackedItemLotId lotId) {
        return "recon:" + serverSessionId + ":" + playerUuid + ":"
                + cycleSequence + ":lot:" + lotId;
    }

    public static ReconciliationCycle create(
            UUID playerUuid,
            String triggerReason,
            long cycleSequence,
            String serverSessionId,
            long scheduledTick
    ) {
        return new ReconciliationCycle(
                UUID.randomUUID(), playerUuid, triggerReason,
                cycleSequence, serverSessionId, scheduledTick
        );
    }
}
