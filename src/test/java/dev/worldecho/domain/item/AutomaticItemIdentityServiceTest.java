package dev.worldecho.domain.item;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.persistence.LotOwnershipLedgerRepository;
import dev.worldecho.persistence.OwnershipLedgerRepository;
import dev.worldecho.persistence.TrackedItemLotRepository;
import dev.worldecho.persistence.TrackedItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pure-Java logic of {@link AutomaticItemIdentityService} using fake
 * in-memory repositories. No Bukkit or Paper dependencies required.
 */
class AutomaticItemIdentityServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneId.of("UTC"));
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final String SESSION_ID = "test-session";

    private FakeTrackedItemRepo trackedItemRepo;
    private FakeLotRepo lotRepo;
    private FakeLedgerRepo ledgerRepo;
    private FakeLotLedgerRepo lotLedgerRepo;
    private OwnershipTransitionService ownershipService;
    private LotOwnershipTransitionService lotOwnershipService;
    private AutomaticItemIdentityService identityService;

    @BeforeEach
    void setUp() {
        trackedItemRepo = new FakeTrackedItemRepo();
        lotRepo = new FakeLotRepo();
        ledgerRepo = new FakeLedgerRepo();
        lotLedgerRepo = new FakeLotLedgerRepo();
        ownershipService = new OwnershipTransitionService(trackedItemRepo, ledgerRepo, FIXED_CLOCK);
        lotOwnershipService = new LotOwnershipTransitionService(lotRepo, lotLedgerRepo, FIXED_CLOCK);
        identityService = new AutomaticItemIdentityService(
                trackedItemRepo, lotRepo, ownershipService, lotOwnershipService, FIXED_CLOCK);
    }

    private ReconciliationCycle cycle(UUID player) {
        return ReconciliationCycle.create(player, "test", 1L, SESSION_ID, 0L);
    }

    private ReconciliationCycle cycle(UUID player, long seq) {
        return ReconciliationCycle.create(player, "test", seq, SESSION_ID, 0L);
    }

    private ObservedInventorySlot uniqueSlot(TrackedItemId existingId) {
        return new ObservedInventorySlot(
                "main", 0, "minecraft:diamond_sword", 1,
                new ContentKey("minecraft", "diamond_sword"), "minecraft",
                ItemDescriptor.of("minecraft:diamond_sword", 1),
                new IdentityClassificationResult(IdentityMode.UNIQUE, List.of("damageable"), 1.0),
                existingId, null, null, false
        );
    }

    private ObservedInventorySlot lotSlot(int slotIndex, int amount) {
        LotCompatibilityFingerprint fingerprint = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();
        return new ObservedInventorySlot(
                "main", slotIndex, "minecraft:cobblestone", amount,
                new ContentKey("minecraft", "cobblestone"), "minecraft",
                ItemDescriptor.of("minecraft:cobblestone", amount),
                new IdentityClassificationResult(IdentityMode.LOT, List.of("stackable"), 1.0),
                null, null, fingerprint, false
        );
    }

    private ObservedInventorySlot lotSlot(int slotIndex, int amount, String material) {
        LotCompatibilityFingerprint fingerprint = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material(material)
                .build();
        return new ObservedInventorySlot(
                "main", slotIndex, material, amount,
                new ContentKey("minecraft", material.replace("minecraft:", "")), "minecraft",
                ItemDescriptor.of(material, amount),
                new IdentityClassificationResult(IdentityMode.LOT, List.of("stackable"), 1.0),
                null, null, fingerprint, false
        );
    }

    private ObservedInventorySlot malformedSlot() {
        return new ObservedInventorySlot(
                "main", 2, "minecraft:iron_sword", 1,
                new ContentKey("minecraft", "iron_sword"), "minecraft",
                ItemDescriptor.of("minecraft:iron_sword", 1),
                new IdentityClassificationResult(IdentityMode.UNIQUE, List.of("damageable"), 1.0),
                null, null, null, true
        );
    }

    private ObservedInventorySnapshot snapshot(UUID player, String name, List<ObservedInventorySlot> slots, ReconciliationCycle cycle) {
        return new ObservedInventorySnapshot(player, name, slots, cycle);
    }

    private TrackedItemLot findLotForPlayer(UUID playerUuid) {
        return lotRepo.lots.values().stream()
                .filter(l -> l.ownerStableId().equals(playerUuid.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No lot found for player " + playerUuid));
    }

    // ==================== UNIQUE item tests (unchanged behavior) ====================

    @Test
    void untrackedUniqueItemReceivesAssignedStatus() {
        ObservedInventorySlot slot = uniqueSlot(null);
        SlotProcessResult result = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycle(PLAYER_A));

        assertEquals(SlotProcessResult.Status.ASSIGNED, result.status());
    }

    @Test
    void existingUniqueIdIsPreservedAndProcessed() {
        TrackedItemId existingId = TrackedItemId.random();
        trackedItemRepo.existing.add(existingId);

        ObservedInventorySlot slot = uniqueSlot(existingId);
        SlotProcessResult result = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycle(PLAYER_A));

        assertEquals(SlotProcessResult.Status.PROCESSED, result.status());
        assertTrue(result.optionalOwnershipResult().isPresent());
    }

    @Test
    void existingUniqueIdMissingDbRecordIsReconciled() {
        TrackedItemId existingId = TrackedItemId.random();

        ObservedInventorySlot slot = uniqueSlot(existingId);
        SlotProcessResult result = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycle(PLAYER_A));

        assertEquals(SlotProcessResult.Status.PROCESSED, result.status());
        assertTrue(trackedItemRepo.existing.contains(existingId),
                "DB record should be created for missing PDC identity");
    }

    @Test
    void malformedIdentityIsNotOverwritten() {
        ObservedInventorySlot slot = malformedSlot();
        SlotProcessResult result = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycle(PLAYER_A));

        assertEquals(SlotProcessResult.Status.MALFORMED, result.status());
    }

    @Test
    void samePlayerReconciliationIsIdempotent() {
        TrackedItemId existingId = TrackedItemId.random();
        trackedItemRepo.existing.add(existingId);

        ReconciliationCycle c = cycle(PLAYER_A);
        ObservedInventorySlot slot = uniqueSlot(existingId);

        SlotProcessResult first = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", c);
        SlotProcessResult second = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", c);

        assertEquals(SlotProcessResult.Status.PROCESSED, first.status());
        assertEquals(SlotProcessResult.Status.PROCESSED, second.status());
        assertEquals(OwnershipResultStatus.IDEMPOTENT_REPLAY, second.ownershipResult().status());
    }

    @Test
    void differentPlayerCreatesOwnershipTransfer() {
        TrackedItemId existingId = TrackedItemId.random();
        trackedItemRepo.existing.add(existingId);

        ObservedInventorySlot slot = uniqueSlot(existingId);

        SlotProcessResult first = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycle(PLAYER_A));
        SlotProcessResult second = identityService.processUniqueSlot(
                slot, PLAYER_B, "PlayerB", cycle(PLAYER_B));

        assertEquals(OwnershipResultStatus.RECORDED, first.ownershipResult().status());
        assertEquals(OwnershipResultStatus.RECORDED, second.ownershipResult().status());
        assertEquals(2, ledgerRepo.entries.size(), "Should have 2 ledger entries");
    }

    @Test
    void oneFailedItemDoesNotBlockOthers() {
        TrackedItemId goodId = TrackedItemId.random();
        trackedItemRepo.failOnNext = true;

        ObservedInventorySlot goodSlot = uniqueSlot(goodId);

        SlotProcessResult result = identityService.processUniqueSlot(
                goodSlot, PLAYER_A, "PlayerA", cycle(PLAYER_A));

        assertEquals(SlotProcessResult.Status.PERSISTENCE_FAILURE, result.status());
        assertNotNull(result.errorMessage());
    }

    @Test
    void duplicateUniqueIdInTwoPlayersDoesNotPingPong() {
        TrackedItemId existingId = TrackedItemId.random();
        trackedItemRepo.existing.add(existingId);

        ObservedInventorySlot slot = uniqueSlot(existingId);

        SlotProcessResult resultA = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycle(PLAYER_A));
        assertEquals(OwnershipResultStatus.RECORDED, resultA.ownershipResult().status());

        SlotProcessResult resultB = identityService.processUniqueSlot(
                slot, PLAYER_B, "PlayerB", cycle(PLAYER_B));
        assertEquals(OwnershipResultStatus.RECORDED, resultB.ownershipResult().status());

        SlotProcessResult resultA2 = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycle(PLAYER_A));
        assertEquals(OwnershipResultStatus.IDEMPOTENT_REPLAY, resultA2.ownershipResult().status(),
                "Same cycle + same player should be idempotent replay");

        ReconciliationCycle cycleA2 = cycle(PLAYER_A, 2L);
        SlotProcessResult resultA3 = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycleA2);
        assertEquals(OwnershipResultStatus.RECORDED, resultA3.ownershipResult().status(),
                "Service-level: ownership transfers back to A");

        ReconciliationCycle cycleA3 = cycle(PLAYER_A, 3L);
        SlotProcessResult resultA4 = identityService.processUniqueSlot(
                slot, PLAYER_A, "PlayerA", cycleA3);
        assertEquals(OwnershipResultStatus.NO_CHANGE, resultA4.ownershipResult().status(),
                "Same player re-observing in a new cycle should be NO_CHANGE when already owner");
    }

    // ==================== LOT aggregation tests ====================

    @Test
    void sameFingerprintStacksAreSummed() {
        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64), lotSlot(1, 32), lotSlot(2, 10)),
                cycle(PLAYER_A));

        identityService.processLotSlots(snap);

        assertEquals(1, lotRepo.lots.size(), "Should create one lot for same fingerprint");
        TrackedItemLot lot = findLotForPlayer(PLAYER_A);
        assertEquals(106, lot.currentAmount(), "64 + 32 + 10 = 106");
    }

    @Test
    void slotOrderDoesNotChangeAggregateAmount() {
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64), lotSlot(1, 32), lotSlot(2, 10)),
                cycle(PLAYER_A));
        identityService.processLotSlots(snap1);

        int amount1 = findLotForPlayer(PLAYER_A).currentAmount();

        // Reset and try different order
        lotRepo.lots.clear();
        lotLedgerRepo.entries.clear();

        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 10), lotSlot(1, 64), lotSlot(2, 32)),
                cycle(PLAYER_A));
        identityService.processLotSlots(snap2);

        int amount2 = findLotForPlayer(PLAYER_A).currentAmount();
        assertEquals(amount1, amount2, "Slot order should not affect aggregate amount");
        assertEquals(106, amount2);
    }

    @Test
    void threeStacksPersistCombinedAmount() {
        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64), lotSlot(1, 32), lotSlot(2, 10)),
                cycle(PLAYER_A));

        identityService.processLotSlots(snap);

        TrackedItemLot lot = findLotForPlayer(PLAYER_A);
        assertEquals(106, lot.currentAmount());
        assertEquals(106, lot.initialAmount());
    }

    @Test
    void splitWithUnchangedTotalPreservesAmount() {
        // First cycle: 64 items
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(64, findLotForPlayer(PLAYER_A).currentAmount());

        // Second cycle: same total but split into two stacks
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32), lotSlot(1, 32)), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);
        assertEquals(64, findLotForPlayer(PLAYER_A).currentAmount(),
                "Split with unchanged total should preserve amount");
    }

    @Test
    void mergeWithUnchangedTotalPreservesAmount() {
        // First cycle: two stacks
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32), lotSlot(1, 32)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(64, findLotForPlayer(PLAYER_A).currentAmount());

        // Second cycle: merged into one stack
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64)), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);
        assertEquals(64, findLotForPlayer(PLAYER_A).currentAmount(),
                "Merge with unchanged total should preserve amount");
    }

    @Test
    void acquisitionIncreasesTotal() {
        // First cycle: 32 items
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(32, findLotForPlayer(PLAYER_A).currentAmount());

        // Second cycle: player picked up more
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32), lotSlot(1, 16)), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);
        assertEquals(48, findLotForPlayer(PLAYER_A).currentAmount(),
                "Acquisition should increase total");
    }

    @Test
    void partialConsumptionDecreasesTotal() {
        // First cycle: 64 items
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(64, findLotForPlayer(PLAYER_A).currentAmount());

        // Second cycle: player used some
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 48)), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);
        assertEquals(48, findLotForPlayer(PLAYER_A).currentAmount(),
                "Partial consumption should decrease total");
    }

    @Test
    void completeRemovalSetsExistingAmountToZero() {
        // First cycle: 64 items
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(64, findLotForPlayer(PLAYER_A).currentAmount());

        // Second cycle: all items gone (empty snapshot)
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);
        assertEquals(0, findLotForPlayer(PLAYER_A).currentAmount(),
                "Complete removal should set amount to zero");
    }

    @Test
    void droppingAllItemsSetsCurrentAmountToZero() {
        // First cycle: 32 cobblestone
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(32, findLotForPlayer(PLAYER_A).currentAmount());

        // Second cycle: snapshot has a different material (cobblestone dropped)
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 16, "minecraft:dirt")), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);

        // Cobblestone lot should be zeroed
        TrackedItemLot cobbleLot = lotRepo.lots.values().stream()
                .filter(l -> l.material().equals("minecraft:cobblestone"))
                .findFirst().orElseThrow();
        assertEquals(0, cobbleLot.currentAmount(), "Dropped items should set amount to zero");
    }

    @Test
    void consumingAllItemsSetsCurrentAmountToZero() {
        // First cycle: 64 cobblestone
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);

        // Second cycle: empty inventory (all consumed)
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);

        assertEquals(0, findLotForPlayer(PLAYER_A).currentAmount(),
                "Consumed items should set amount to zero");
    }

    @Test
    void absentUnknownFingerprintDoesNotCreateLot() {
        // Empty snapshot should not create any lots
        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(), cycle(PLAYER_A));
        identityService.processLotSlots(snap);

        assertEquals(0, lotRepo.lots.size(), "Empty snapshot should not create lots");
    }

    @Test
    void existingAbsentFingerprintBecomesZero() {
        // First cycle: cobblestone and dirt
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32), lotSlot(1, 16, "minecraft:dirt")),
                cycle(PLAYER_A));
        identityService.processLotSlots(snap1);

        // Second cycle: only cobblestone, dirt is gone
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32)), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);

        TrackedItemLot dirtLot = lotRepo.lots.values().stream()
                .filter(l -> l.material().equals("minecraft:dirt"))
                .findFirst().orElseThrow();
        assertEquals(0, dirtLot.currentAmount(), "Absent fingerprint should be zeroed");
    }

    @Test
    void repeatedIdenticalSnapshotIsIdempotent() {
        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64)), cycle(PLAYER_A));

        identityService.processLotSlots(snap);
        int amountAfterFirst = findLotForPlayer(PLAYER_A).currentAmount();

        // Same cycle again
        identityService.processLotSlots(snap);
        int amountAfterSecond = findLotForPlayer(PLAYER_A).currentAmount();

        assertEquals(amountAfterFirst, amountAfterSecond, "Repeated identical snapshot should be idempotent");
        assertEquals(64, amountAfterSecond);
    }

    @Test
    void oneUpdatePerOwnerFingerprintPerCycle() {
        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64), lotSlot(1, 32), lotSlot(2, 10)),
                cycle(PLAYER_A));

        List<SlotProcessResult> results = identityService.processLotSlots(snap);

        // 3 stacks of same fingerprint → 1 result (one update per owner+fingerprint)
        assertEquals(1, results.size(), "Should produce one result per fingerprint group");
        assertEquals(1, lotRepo.lots.size(), "Should create one lot");
        assertEquals(106, findLotForPlayer(PLAYER_A).currentAmount());
    }

    @Test
    void sameUuidAfterNameChangeUsesSameAggregate() {
        // First cycle with name "PlayerA"
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(1, lotRepo.lots.size());

        // Second cycle with different display name but same UUID
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "NewName",
                List.of(lotSlot(0, 48)), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);

        assertEquals(1, lotRepo.lots.size(), "Same UUID after name change should reuse aggregate");
        assertEquals(48, findLotForPlayer(PLAYER_A).currentAmount());
    }

    @Test
    void differentPlayerUuidsUseDifferentAggregates() {
        ObservedInventorySnapshot snapA = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32)), cycle(PLAYER_A));
        identityService.processLotSlots(snapA);

        ObservedInventorySnapshot snapB = snapshot(PLAYER_B, "PlayerB",
                List.of(lotSlot(0, 64)), cycle(PLAYER_B));
        identityService.processLotSlots(snapB);

        assertEquals(2, lotRepo.lots.size(), "Different players should get different lots");
        assertEquals(32, findLotForPlayer(PLAYER_A).currentAmount());
        assertEquals(64, findLotForPlayer(PLAYER_B).currentAmount());
    }

    @Test
    void displayNameDoesNotAffectLookup() {
        // Create with one name
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 32)), cycle(PLAYER_A));
        identityService.processLotSlots(snap1);

        // Update with completely different name
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "CompletelyDifferent",
                List.of(lotSlot(0, 48)), cycle(PLAYER_A, 2L));
        identityService.processLotSlots(snap2);

        assertEquals(1, lotRepo.lots.size(), "Display name should not affect lookup");
        TrackedItemLot lot = findLotForPlayer(PLAYER_A);
        assertEquals(48, lot.currentAmount());
    }

    @Test
    void repositoryRestartPreservesAggregateTotal() {
        // First cycle: 106 items
        ObservedInventorySnapshot snap1 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 64), lotSlot(1, 32), lotSlot(2, 10)),
                cycle(PLAYER_A));
        identityService.processLotSlots(snap1);
        assertEquals(106, findLotForPlayer(PLAYER_A).currentAmount());

        // Simulate restart: new service instance, same repo state
        AutomaticItemIdentityService restartedService = new AutomaticItemIdentityService(
                trackedItemRepo, lotRepo, ownershipService, lotOwnershipService, FIXED_CLOCK);

        // New cycle with different amount
        ObservedInventorySnapshot snap2 = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 48)), cycle(PLAYER_A, 2L));
        restartedService.processLotSlots(snap2);

        assertEquals(1, lotRepo.lots.size(), "Restart should find existing lot by owner+fingerprint");
        assertEquals(48, findLotForPlayer(PLAYER_A).currentAmount());
    }

    @Test
    void malformedItemDoesNotBlockValidAggregation() {
        ObservedInventorySlot malformedLotSlot = new ObservedInventorySlot(
                "main", 0, "minecraft:cobblestone", 32,
                new ContentKey("minecraft", "cobblestone"), "minecraft",
                ItemDescriptor.of("minecraft:cobblestone", 32),
                new IdentityClassificationResult(IdentityMode.LOT, List.of("stackable"), 1.0),
                null, null, null, true
        );

        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(malformedLotSlot, lotSlot(1, 64), lotSlot(2, 32)),
                cycle(PLAYER_A));

        List<SlotProcessResult> results = identityService.processLotSlots(snap);

        // Malformed slot should produce a MALFORMED result, valid slots should aggregate
        boolean hasMalformed = results.stream()
                .anyMatch(r -> r.status() == SlotProcessResult.Status.MALFORMED);
        assertTrue(hasMalformed, "Malformed item should produce MALFORMED result");

        // Valid items should still aggregate
        assertEquals(1, lotRepo.lots.size(), "Valid items should still be aggregated");
        assertEquals(96, findLotForPlayer(PLAYER_A).currentAmount(),
                "64 + 32 = 96 (malformed slot excluded)");
    }

    @Test
    void amountCannotBecomeNegative() {
        // The service should never persist a negative amount
        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(lotSlot(0, 0)), cycle(PLAYER_A));
        identityService.processLotSlots(snap);

        // Zero-amount slot is empty, should not create a lot
        assertEquals(0, lotRepo.lots.size(), "Zero-amount slot should not create lot");
    }

    @Test
    void joinReconciliationPlanGeneratesSnapshot() {
        TrackedItemId uniqueId = TrackedItemId.random();
        trackedItemRepo.existing.add(uniqueId);

        ObservedInventorySnapshot snap = snapshot(PLAYER_A, "PlayerA",
                List.of(uniqueSlot(uniqueId), lotSlot(1, 32)),
                cycle(PLAYER_A));

        // Process UNIQUE items individually
        for (ObservedInventorySlot slot : snap.slots()) {
            if (slot.isUnique()) {
                identityService.processUniqueSlot(slot, PLAYER_A, "PlayerA", snap.cycle());
            }
        }
        // Process LOT items as batch
        identityService.processLotSlots(snap);

        assertEquals(1, trackedItemRepo.existing.size());
        assertEquals(1, lotRepo.lots.size());
        assertTrue(ledgerRepo.entries.size() >= 1);
        assertTrue(lotLedgerRepo.entries.size() >= 1);
    }

    // ==================== Fake repositories ====================

    private static class FakeTrackedItemRepo implements TrackedItemRepository {
        final java.util.Set<TrackedItemId> existing = new java.util.HashSet<>();
        boolean failOnNext = false;

        @Override
        public CreateResult create(TrackedItemRecord record) {
            if (failOnNext) {
                failOnNext = false;
                throw new RuntimeException("simulated failure");
            }
            return existing.add(record.itemId()) ? CreateResult.CREATED : CreateResult.ALREADY_EXISTS;
        }

        @Override
        public Optional<TrackedItemRecord> findById(TrackedItemId itemId) {
            return Optional.empty();
        }

        @Override
        public boolean exists(TrackedItemId itemId) {
            return existing.contains(itemId);
        }

        @Override
        public void observe(TrackedItemId itemId, long lastSeenEpochMilli) {
        }

        @Override
        public long count() {
            return existing.size();
        }
    }

    private static class FakeLotRepo implements TrackedItemLotRepository {
        final Map<TrackedItemLotId, TrackedItemLot> lots = new HashMap<>();
        final List<LotLineageEntry> lineage = new ArrayList<>();
        boolean failReconcile = false;

        @Override
        public ReconcileResult reconcileOwnerAggregates(
                OwnershipSubjectType ownerType,
                String ownerStableId,
                String ownerDisplaySnapshot,
                Map<LotCompatibilityFingerprint, Integer> observedAmountsByFingerprint,
                Instant observedAt
        ) {
            if (failReconcile) {
                return ReconcileResult.FAILURE;
            }
            String typeToken = ownerType.token();
            String normalizedStableId = ownerStableId.toLowerCase(java.util.Locale.ROOT);

            // Snapshot current state for potential rollback
            Map<TrackedItemLotId, TrackedItemLot> snapshot = new HashMap<>(lots);

            try {
                java.util.Set<String> observedFingerprints = new java.util.HashSet<>();

                // Upsert observed fingerprints
                for (Map.Entry<LotCompatibilityFingerprint, Integer> entry : observedAmountsByFingerprint.entrySet()) {
                    String fpSerialized = entry.getKey().serialize();
                    int totalAmount = Math.max(0, entry.getValue());
                    observedFingerprints.add(fpSerialized);

                    Optional<TrackedItemLot> existing = lots.values().stream()
                            .filter(l -> l.ownerType().equals(typeToken)
                                    && l.ownerStableId().equals(normalizedStableId)
                                    && l.fingerprint().equals(entry.getKey()))
                            .findFirst();

                    if (existing.isPresent()) {
                        TrackedItemLot lot = existing.get();
                        lots.put(lot.lotId(), lot.withCurrentAmount(totalAmount).withLastSeenAt(observedAt)
                                .withOwnerDisplaySnapshot(ownerDisplaySnapshot));
                    } else {
                        TrackedItemLotId newLotId = TrackedItemLotId.random();
                        TrackedItemLot lot = new TrackedItemLot(
                                newLotId, observedAt, observedAt, observedAt,
                                new ContentKey(entry.getKey().providerId(), entry.getKey().material()),
                                entry.getKey().providerId(),
                                entry.getKey().material(),
                                entry.getKey(),
                                totalAmount, totalAmount, "AUTOMATIC",
                                typeToken + ":" + normalizedStableId,
                                typeToken, normalizedStableId, ownerDisplaySnapshot
                        );
                        lots.put(newLotId, lot);
                    }
                }

                // Zero absent fingerprints
                for (TrackedItemLot lot : lots.values()) {
                    if (lot.ownerType().equals(typeToken)
                            && lot.ownerStableId().equals(normalizedStableId)
                            && lot.currentAmount() > 0
                            && !observedFingerprints.contains(lot.fingerprint().serialize())) {
                        lots.put(lot.lotId(), lot.withCurrentAmount(0).withLastSeenAt(observedAt));
                    }
                }

                return ReconcileResult.SUCCESS;
            } catch (Exception e) {
                // Rollback
                lots.clear();
                lots.putAll(snapshot);
                return ReconcileResult.FAILURE;
            }
        }

        @Override
        public CreateResult create(TrackedItemLot lot) throws SQLException {
            if (lots.containsKey(lot.lotId())) {
                return CreateResult.ALREADY_EXISTS;
            }
            lots.put(lot.lotId(), lot);
            return CreateResult.CREATED;
        }

        @Override
        public Optional<TrackedItemLot> findById(TrackedItemLotId lotId) throws SQLException {
            return Optional.ofNullable(lots.get(lotId));
        }

        @Override
        public Optional<TrackedItemLot> findByFingerprint(LotCompatibilityFingerprint fingerprint) throws SQLException {
            return lots.values().stream()
                    .filter(l -> l.fingerprint().equals(fingerprint))
                    .findFirst();
        }

        @Override
        public Optional<TrackedItemLot> findByFingerprintAndOwner(
                LotCompatibilityFingerprint fingerprint, String ownerSubject) throws SQLException {
            return lots.values().stream()
                    .filter(l -> l.fingerprint().equals(fingerprint)
                            && l.createdBySubject().equals(ownerSubject))
                    .findFirst();
        }

        @Override
        public Optional<TrackedItemLot> findByOwnerAndFingerprint(
                OwnershipSubjectType ownerType, String ownerStableId,
                LotCompatibilityFingerprint fingerprint) throws SQLException {
            return lots.values().stream()
                    .filter(l -> l.fingerprint().equals(fingerprint)
                            && l.ownerType().equals(ownerType.token())
                            && l.ownerStableId().equals(ownerStableId))
                    .findFirst();
        }

        @Override
        public List<TrackedItemLot> findAllByOwner(
                OwnershipSubjectType ownerType, String ownerStableId) throws SQLException {
            return lots.values().stream()
                    .filter(l -> l.ownerType().equals(ownerType.token())
                            && l.ownerStableId().equals(ownerStableId))
                    .toList();
        }

        @Override
        public void updateOwnerDisplaySnapshot(TrackedItemLotId lotId, String displayName) throws SQLException {
            TrackedItemLot lot = lots.get(lotId);
            if (lot != null) {
                lots.put(lotId, lot.withOwnerDisplaySnapshot(displayName));
            }
        }

        @Override
        public boolean exists(TrackedItemLotId lotId) throws SQLException {
            return lots.containsKey(lotId);
        }

        @Override
        public void updateAmount(TrackedItemLotId lotId, int newAmount) throws SQLException {
            TrackedItemLot lot = lots.get(lotId);
            if (lot != null) {
                lots.put(lotId, lot.withCurrentAmount(newAmount));
            }
        }

        @Override
        public void observe(TrackedItemLotId lotId, Instant lastSeenAt) throws SQLException {
            TrackedItemLot lot = lots.get(lotId);
            if (lot != null) {
                lots.put(lotId, lot.withLastSeenAt(lastSeenAt));
            }
        }

        @Override
        public int count() throws SQLException {
            return lots.size();
        }

        @Override
        public void appendLineage(LotLineageEntry entry) throws SQLException {
            lineage.add(entry);
        }

        @Override
        public Optional<LotLineageEntry> findLineageByIdempotencyKey(TrackedItemLotId lotId, String idempotencyKey) throws SQLException {
            return lineage.stream()
                    .filter(e -> e.lotId().equals(lotId) && e.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public List<LotLineageEntry> findLineage(TrackedItemLotId lotId) throws SQLException {
            return lineage.stream().filter(e -> e.lotId().equals(lotId)).toList();
        }
    }

    private static class FakeLedgerRepo implements OwnershipLedgerRepository {
        final List<OwnershipLedgerEntry> entries = new ArrayList<>();
        final Map<String, OwnershipLedgerEntry> byIdempotency = new HashMap<>();

        @Override
        public AppendResult append(OwnershipLedgerEntry entry) {
            String key = entry.itemId() + ":" + entry.idempotencyKey();
            if (entry.hasIdempotencyKey() && byIdempotency.containsKey(key)) {
                return AppendResult.IDEMPOTENT_REPLAY;
            }
            for (OwnershipLedgerEntry existing : entries) {
                if (existing.itemId().equals(entry.itemId())
                        && existing.sequenceNumber() == entry.sequenceNumber()) {
                    return AppendResult.CONFLICT;
                }
            }
            entries.add(entry);
            if (entry.hasIdempotencyKey()) {
                byIdempotency.put(key, entry);
            }
            return AppendResult.APPENDED;
        }

        @Override
        public Optional<OwnershipState> findCurrentOwnership(TrackedItemId itemId) {
            OwnershipLedgerEntry latest = null;
            long count = 0;
            for (OwnershipLedgerEntry entry : entries) {
                if (entry.itemId().equals(itemId)) {
                    count++;
                    if (latest == null || entry.sequenceNumber() > latest.sequenceNumber()) {
                        latest = entry;
                    }
                }
            }
            if (latest == null) return Optional.empty();
            return Optional.of(new OwnershipState(
                    itemId, latest.newSubject(), latest.sequenceNumber(),
                    latest.transitionReason(), latest.occurredAt(), count));
        }

        @Override
        public List<OwnershipLedgerEntry> findHistory(TrackedItemId itemId, int limit) {
            return entries.stream()
                    .filter(e -> e.itemId().equals(itemId))
                    .sorted((a, b) -> Integer.compare(b.sequenceNumber(), a.sequenceNumber()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countHistory(TrackedItemId itemId) {
            return entries.stream().filter(e -> e.itemId().equals(itemId)).count();
        }

        @Override
        public Optional<OwnershipLedgerEntry> findByIdempotencyKey(TrackedItemId itemId, String idempotencyKey) {
            if (idempotencyKey == null || idempotencyKey.isEmpty()) return Optional.empty();
            return Optional.ofNullable(byIdempotency.get(itemId + ":" + idempotencyKey));
        }

        @Override
        public long count() {
            return entries.size();
        }
    }

    private static class FakeLotLedgerRepo implements LotOwnershipLedgerRepository {
        final List<LotOwnershipLedgerEntry> entries = new ArrayList<>();
        final Map<String, LotOwnershipLedgerEntry> byIdempotency = new HashMap<>();

        @Override
        public AppendResult append(LotOwnershipLedgerEntry entry) throws SQLException {
            String key = entry.lotId() + ":" + entry.idempotencyKey();
            if (!entry.idempotencyKey().isEmpty() && byIdempotency.containsKey(key)) {
                return AppendResult.IDEMPOTENT_REPLAY;
            }
            entries.add(entry);
            if (!entry.idempotencyKey().isEmpty()) {
                byIdempotency.put(key, entry);
            }
            return AppendResult.APPENDED;
        }

        @Override
        public Optional<LotOwnershipState> findCurrentOwnership(TrackedItemLotId lotId) throws SQLException {
            LotOwnershipLedgerEntry latest = null;
            long count = 0;
            for (LotOwnershipLedgerEntry entry : entries) {
                if (entry.lotId().equals(lotId)) {
                    count++;
                    if (latest == null || entry.sequenceNumber() > latest.sequenceNumber()) {
                        latest = entry;
                    }
                }
            }
            if (latest == null) return Optional.empty();
            return Optional.of(new LotOwnershipState(
                    lotId, latest.newSubject(), latest.sequenceNumber(),
                    latest.transitionReason(), latest.occurredAt(), count));
        }

        @Override
        public Optional<LotOwnershipLedgerEntry> findByIdempotencyKey(TrackedItemLotId lotId, String idempotencyKey) throws SQLException {
            if (idempotencyKey == null || idempotencyKey.isEmpty()) return Optional.empty();
            return Optional.ofNullable(byIdempotency.get(lotId + ":" + idempotencyKey));
        }

        @Override
        public List<LotOwnershipLedgerEntry> findHistory(TrackedItemLotId lotId, int limit) throws SQLException {
            return entries.stream()
                    .filter(e -> e.lotId().equals(lotId))
                    .sorted((a, b) -> Integer.compare(b.sequenceNumber(), a.sequenceNumber()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countHistory(TrackedItemLotId lotId) throws SQLException {
            return entries.stream().filter(e -> e.lotId().equals(lotId)).count();
        }

        @Override
        public long countAll() throws SQLException {
            return entries.size();
        }
    }
}
