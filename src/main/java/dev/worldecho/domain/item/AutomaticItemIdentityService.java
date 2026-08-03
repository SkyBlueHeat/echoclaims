package dev.worldecho.domain.item;

import dev.worldecho.persistence.TrackedItemLotRepository;
import dev.worldecho.persistence.TrackedItemRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates automatic identity assignment and persistence for observed inventory items.
 *
 * <p>Called on the background thread with immutable snapshots captured on the main thread.
 */
public final class AutomaticItemIdentityService {

    private final TrackedItemRepository trackedItemRepository;
    private final TrackedItemLotRepository lotRepository;
    private final OwnershipTransitionService ownershipTransitionService;
    private final LotOwnershipTransitionService lotOwnershipTransitionService;
    private final Clock clock;

    public AutomaticItemIdentityService(
            TrackedItemRepository trackedItemRepository,
            TrackedItemLotRepository lotRepository,
            OwnershipTransitionService ownershipTransitionService,
            LotOwnershipTransitionService lotOwnershipTransitionService,
            Clock clock
    ) {
        this.trackedItemRepository = Objects.requireNonNull(trackedItemRepository, "trackedItemRepository");
        this.lotRepository = Objects.requireNonNull(lotRepository, "lotRepository");
        this.ownershipTransitionService = Objects.requireNonNull(ownershipTransitionService, "ownershipTransitionService");
        this.lotOwnershipTransitionService = Objects.requireNonNull(lotOwnershipTransitionService, "lotOwnershipTransitionService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Processes a single observed slot for a UNIQUE item.
     *
     * @return the result of processing
     */
    public SlotProcessResult processUniqueSlot(
            ObservedInventorySlot slot,
            UUID playerUuid,
            String playerDisplayName,
            ReconciliationCycle cycle
    ) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(cycle, "cycle");

        if (slot.malformedIdentity()) {
            return SlotProcessResult.malformed(slot);
        }

        TrackedItemId itemId = slot.existingUniqueId();
        if (itemId == null) {
            return SlotProcessResult.assigned(slot);
        }

        try {
            if (!trackedItemRepository.exists(itemId)) {
                Instant now = Instant.now(clock);
                TrackedItemRecord record = new TrackedItemRecord(
                        itemId, now, now, now,
                        slot.contentKey(),
                        slot.providerId(),
                        slot.material(),
                        slot.descriptor() != null && slot.descriptor().hasCustomName()
                                ? slot.descriptor().customName() : "",
                        slot.descriptor() != null ? null : null,
                        "AUTOMATIC",
                        OwnershipSubject.player(playerUuid, playerDisplayName).describe()
                );
                trackedItemRepository.create(record);
            } else {
                trackedItemRepository.observe(itemId, Instant.now(clock).toEpochMilli());
            }

            OwnershipSubject playerSubject = OwnershipSubject.player(playerUuid, playerDisplayName);
            String idempotencyKey = cycle.idempotencyKeyFor(itemId);
            OwnershipResult ownershipResult = ownershipTransitionService.transition(
                    itemId, playerSubject,
                    OwnershipTransitionReason.AUTOMATIC_TRACKING,
                    idempotencyKey,
                    "automatic-reconciliation",
                    "cycle=" + cycle.cycleSequence()
            );

            return SlotProcessResult.processed(slot, ownershipResult);
        } catch (Exception exception) {
            return SlotProcessResult.persistenceFailure(slot, exception.getMessage());
        }
    }

    /**
     * Processes all LOT slots in a snapshot as a single aggregation unit.
     *
     * <p>Groups LOT slots by fingerprint, sums amounts per group, and performs
     * one atomic transaction per owner per cycle: upserts observed fingerprints,
     * zeros absent ones, and updates the display snapshot. No partial state
     * is left if any SQL operation fails.
     *
     * @return list of results, one per input LOT slot (for metrics)
     */
    public List<SlotProcessResult> processLotSlots(
            ObservedInventorySnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        UUID playerUuid = snapshot.playerUuid();
        String playerDisplayName = snapshot.playerDisplayName();
        ReconciliationCycle cycle = snapshot.cycle();

        List<SlotProcessResult> results = new ArrayList<>();

        // Separate valid LOT slots from malformed ones
        List<ObservedInventorySlot> lotSlots = new ArrayList<>();
        for (ObservedInventorySlot slot : snapshot.slots()) {
            if (!slot.isLot()) {
                continue;
            }
            if (slot.malformedIdentity()) {
                results.add(SlotProcessResult.malformed(slot));
                continue;
            }
            if (slot.isEmpty()) {
                continue;
            }
            lotSlots.add(slot);
        }

        // Group by fingerprint and sum amounts
        Map<LotCompatibilityFingerprint, Integer> aggregatedAmounts = new LinkedHashMap<>();
        Map<LotCompatibilityFingerprint, ObservedInventorySlot> representativeSlots = new LinkedHashMap<>();
        for (ObservedInventorySlot slot : lotSlots) {
            LotCompatibilityFingerprint fingerprint = slot.lotFingerprint();
            if (fingerprint == null) {
                fingerprint = LotCompatibilityFingerprint.builder()
                        .providerId(slot.providerId())
                        .material(slot.material())
                        .build();
            }
            aggregatedAmounts.merge(fingerprint, slot.amount(), Integer::sum);
            representativeSlots.putIfAbsent(fingerprint, slot);
        }

        // Perform atomic reconciliation: upsert + zero in one transaction
        String ownerStableId = playerUuid.toString();
        TrackedItemLotRepository.ReconcileResult reconcileResult = lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER,
                ownerStableId,
                playerDisplayName,
                aggregatedAmounts,
                Instant.now(clock)
        );

        if (reconcileResult == TrackedItemLotRepository.ReconcileResult.FAILURE) {
            // Report structured failure for each representative slot
            for (Map.Entry<LotCompatibilityFingerprint, ObservedInventorySlot> entry : representativeSlots.entrySet()) {
                results.add(SlotProcessResult.persistenceFailure(
                        entry.getValue(), "reconcileOwnerAggregates transaction failed"));
            }
            return results;
        }

        // Success: record ownership transitions for each fingerprint group
        for (Map.Entry<LotCompatibilityFingerprint, Integer> entry : aggregatedAmounts.entrySet()) {
            LotCompatibilityFingerprint fingerprint = entry.getKey();
            ObservedInventorySlot repSlot = representativeSlots.get(fingerprint);

            try {
                Optional<TrackedItemLot> lot = lotRepository.findByOwnerAndFingerprint(
                        OwnershipSubjectType.PLAYER, ownerStableId, fingerprint);
                if (lot.isEmpty()) {
                    results.add(SlotProcessResult.persistenceFailure(repSlot,
                            "Lot not found after successful reconciliation"));
                    continue;
                }

                TrackedItemLotId lotId = lot.get().lotId();
                OwnershipSubject playerSubject = OwnershipSubject.player(playerUuid, playerDisplayName);
                String idempotencyKey = cycle.idempotencyKeyFor(lotId);
                OwnershipResult ownershipResult = lotOwnershipTransitionService.transition(
                        lotId, playerSubject,
                        OwnershipTransitionReason.AUTOMATIC_TRACKING,
                        idempotencyKey,
                        "automatic-reconciliation",
                        "cycle=" + cycle.cycleSequence()
                );

                results.add(SlotProcessResult.processed(repSlot, ownershipResult));
            } catch (Exception exception) {
                results.add(SlotProcessResult.persistenceFailure(repSlot, exception.getMessage()));
            }
        }

        return results;
    }
}
