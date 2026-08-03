package dev.worldecho.domain.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure-Java, testable component that generates a reconciliation plan from an
 * {@link ObservedInventorySnapshot}.
 *
 * <p>Encapsulates the decision logic that was previously embedded inside the
 * Bukkit-dependent {@code PlayerInventoryReconciler.processSnapshot} method.
 * The reconciler delegates to this class so that plan generation is
 * unit-testable without Bukkit.
 *
 * <p>For each non-empty slot, the plan decides:
 * <ul>
 *   <li>Whether to skip the slot (empty, unclassified, or duplicate)</li>
 *   <li>Whether to process as UNIQUE or LOT</li>
 *   <li>Whether to check for duplicate identity before processing</li>
 * </ul>
 *
 * <p>The plan does NOT execute any persistence — it only describes what should happen.
 * Execution is performed by {@link AutomaticItemIdentityService}.
 */
public final class ReconciliationPlanGenerator {

    public enum SlotAction {
        PROCESS_UNIQUE,
        PROCESS_UNIQUE_WITH_DUPLICATE_CHECK,
        PROCESS_LOT,
        SKIP_EMPTY,
        SKIP_UNCLASSIFIED,
        SKIP_DUPLICATE
    }

    public record SlotPlan(
            ObservedInventorySlot slot,
            SlotAction action
    ) {
        public SlotPlan {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(action, "action");
        }
    }

    public record ReconciliationPlan(
            UUID playerUuid,
            String playerDisplayName,
            ReconciliationCycle cycle,
            List<SlotPlan> slotPlans
    ) {
        public ReconciliationPlan {
            Objects.requireNonNull(playerUuid, "playerUuid");
            playerDisplayName = playerDisplayName == null ? "" : playerDisplayName.strip();
            slotPlans = slotPlans == null ? List.of() : List.copyOf(slotPlans);
        }

        public int actionableCount() {
            return (int) slotPlans.stream()
                    .filter(p -> p.action() != SlotAction.SKIP_EMPTY
                            && p.action() != SlotAction.SKIP_UNCLASSIFIED)
                    .count();
        }

        public int uniqueCount() {
            return (int) slotPlans.stream()
                    .filter(p -> p.action() == SlotAction.PROCESS_UNIQUE
                            || p.action() == SlotAction.PROCESS_UNIQUE_WITH_DUPLICATE_CHECK)
                    .count();
        }

        public int lotCount() {
            return (int) slotPlans.stream()
                    .filter(p -> p.action() == SlotAction.PROCESS_LOT)
                    .count();
        }

        public int skipCount() {
            return (int) slotPlans.stream()
                    .filter(p -> p.action().name().startsWith("SKIP"))
                    .count();
        }
    }

    /**
     * Generates a reconciliation plan from a snapshot.
     *
     * @param snapshot the observed inventory snapshot
     * @return a plan describing what action to take for each slot
     */
    public ReconciliationPlan generatePlan(ObservedInventorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        List<SlotPlan> plans = new ArrayList<>();
        for (ObservedInventorySlot slot : snapshot.slots()) {
            plans.add(planSlot(slot));
        }

        return new ReconciliationPlan(
                snapshot.playerUuid(),
                snapshot.playerDisplayName(),
                snapshot.cycle(),
                plans
        );
    }

    private SlotPlan planSlot(ObservedInventorySlot slot) {
        if (slot.isEmpty()) {
            return new SlotPlan(slot, SlotAction.SKIP_EMPTY);
        }

        if (slot.isUnique()) {
            if (slot.existingUniqueId() != null) {
                return new SlotPlan(slot, SlotAction.PROCESS_UNIQUE_WITH_DUPLICATE_CHECK);
            }
            return new SlotPlan(slot, SlotAction.PROCESS_UNIQUE);
        }

        if (slot.isLot()) {
            return new SlotPlan(slot, SlotAction.PROCESS_LOT);
        }

        return new SlotPlan(slot, SlotAction.SKIP_UNCLASSIFIED);
    }
}
