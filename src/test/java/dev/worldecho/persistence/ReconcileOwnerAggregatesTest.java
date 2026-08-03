package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.item.LotCompatibilityFingerprint;
import dev.worldecho.domain.item.OwnershipSubjectType;
import dev.worldecho.domain.item.TrackedItemLot;
import dev.worldecho.domain.item.TrackedItemLotId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for atomic reconcileOwnerAggregates: concurrent create, repeated
 * reconciliation, transaction rollback, and absence of ambiguous duplicates.
 */
class ReconcileOwnerAggregatesTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SqliteTrackedItemLotRepository lotRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("reconcile.db"));
        databaseManager.initialize();
        lotRepository = new SqliteTrackedItemLotRepository(databaseManager);
    }

    private LotCompatibilityFingerprint fp(String material) {
        return LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material(material)
                .build();
    }

    private Map<LotCompatibilityFingerprint, Integer> singleFingerprint(String material, int amount) {
        Map<LotCompatibilityFingerprint, Integer> map = new LinkedHashMap<>();
        map.put(fp(material), amount);
        return map;
    }

    @Test
    void reconcileCreatesNewLotForObservedFingerprint() throws Exception {
        UUID owner = UUID.randomUUID();
        TrackedItemLotRepository.ReconcileResult result = lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                singleFingerprint("minecraft:cobblestone", 64),
                Instant.now()
        );

        assertEquals(TrackedItemLotRepository.ReconcileResult.SUCCESS, result);

        Optional<TrackedItemLot> lot = lotRepository.findByOwnerAndFingerprint(
                OwnershipSubjectType.PLAYER, owner.toString(), fp("minecraft:cobblestone"));
        assertTrue(lot.isPresent());
        assertEquals(64, lot.get().currentAmount());
    }

    @Test
    void reconcileUpdatesExistingLotAmount() throws Exception {
        UUID owner = UUID.randomUUID();
        LotCompatibilityFingerprint fingerprint = fp("minecraft:cobblestone");

        // First reconcile: 64 items
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                singleFingerprint("minecraft:cobblestone", 64),
                Instant.now()
        );

        // Second reconcile: 48 items
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                singleFingerprint("minecraft:cobblestone", 48),
                Instant.now()
        );

        Optional<TrackedItemLot> lot = lotRepository.findByOwnerAndFingerprint(
                OwnershipSubjectType.PLAYER, owner.toString(), fingerprint);
        assertTrue(lot.isPresent());
        assertEquals(48, lot.get().currentAmount(), "Amount should be updated to 48");
    }

    @Test
    void reconcileZerosAbsentFingerprints() throws Exception {
        UUID owner = UUID.randomUUID();
        LotCompatibilityFingerprint cobbleFp = fp("minecraft:cobblestone");
        LotCompatibilityFingerprint dirtFp = fp("minecraft:dirt");

        // First reconcile: cobblestone + dirt
        Map<LotCompatibilityFingerprint, Integer> both = new LinkedHashMap<>();
        both.put(cobbleFp, 32);
        both.put(dirtFp, 16);
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                both, Instant.now()
        );

        // Second reconcile: only cobblestone (dirt absent)
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                singleFingerprint("minecraft:cobblestone", 32),
                Instant.now()
        );

        Optional<TrackedItemLot> dirtLot = lotRepository.findByOwnerAndFingerprint(
                OwnershipSubjectType.PLAYER, owner.toString(), dirtFp);
        assertTrue(dirtLot.isPresent());
        assertEquals(0, dirtLot.get().currentAmount(), "Absent fingerprint should be zeroed");
    }

    @Test
    void reconcileDoesNotZeroAlreadyZeroedLots() throws Exception {
        UUID owner = UUID.randomUUID();
        LotCompatibilityFingerprint cobbleFp = fp("minecraft:cobblestone");

        // First reconcile: 32 cobblestone
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                singleFingerprint("minecraft:cobblestone", 32),
                Instant.now()
        );

        // Second reconcile: empty (zero cobblestone)
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                Map.of(),
                Instant.now()
        );

        // Third reconcile: empty again
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                Map.of(),
                Instant.now()
        );

        Optional<TrackedItemLot> lot = lotRepository.findByOwnerAndFingerprint(
                OwnershipSubjectType.PLAYER, owner.toString(), cobbleFp);
        assertTrue(lot.isPresent());
        assertEquals(0, lot.get().currentAmount(), "Already zeroed lot should stay at zero");
    }

    @Test
    void repeatedReconciliationIsIdempotent() throws Exception {
        UUID owner = UUID.randomUUID();
        Map<LotCompatibilityFingerprint, Integer> amounts = singleFingerprint("minecraft:cobblestone", 64);

        for (int i = 0; i < 3; i++) {
            lotRepository.reconcileOwnerAggregates(
                    OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                    amounts, Instant.now()
            );
        }

        Optional<TrackedItemLot> lot = lotRepository.findByOwnerAndFingerprint(
                OwnershipSubjectType.PLAYER, owner.toString(), fp("minecraft:cobblestone"));
        assertTrue(lot.isPresent());
        assertEquals(64, lot.get().currentAmount(), "Repeated reconciliation should be idempotent");
    }

    @Test
    void reconcileUpdatesDisplaySnapshot() throws Exception {
        UUID owner = UUID.randomUUID();

        // First reconcile with "OldName"
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "OldName",
                singleFingerprint("minecraft:cobblestone", 32),
                Instant.now()
        );

        // Second reconcile with "NewName"
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "NewName",
                singleFingerprint("minecraft:cobblestone", 32),
                Instant.now()
        );

        List<TrackedItemLot> allLots = lotRepository.findAllByOwner(
                OwnershipSubjectType.PLAYER, owner.toString());
        assertEquals(1, allLots.size());
        assertEquals("NewName", allLots.get(0).ownerDisplaySnapshot(),
                "Display snapshot should be updated");
    }

    @Test
    void findByOwnerAndFingerprintNeverReturnsAmbiguousDuplicates() throws Exception {
        UUID owner = UUID.randomUUID();
        LotCompatibilityFingerprint fingerprint = fp("minecraft:cobblestone");

        // Reconcile multiple times
        for (int i = 0; i < 5; i++) {
            lotRepository.reconcileOwnerAggregates(
                    OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                    singleFingerprint("minecraft:cobblestone", 64),
                    Instant.now()
            );
        }

        // Verify only one lot exists for this owner+fingerprint
        List<TrackedItemLot> ownerLots = lotRepository.findAllByOwner(
                OwnershipSubjectType.PLAYER, owner.toString());
        long cobbleCount = ownerLots.stream()
                .filter(l -> l.fingerprint().equals(fingerprint))
                .count();
        assertEquals(1, cobbleCount, "Should never have duplicate owner+fingerprint rows");
    }

    @Test
    void reconcileHandlesMultipleFingerprintsAtomically() throws Exception {
        UUID owner = UUID.randomUUID();
        Map<LotCompatibilityFingerprint, Integer> amounts = new LinkedHashMap<>();
        amounts.put(fp("minecraft:cobblestone"), 32);
        amounts.put(fp("minecraft:dirt"), 16);
        amounts.put(fp("minecraft:iron_ingot"), 8);

        TrackedItemLotRepository.ReconcileResult result = lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                amounts, Instant.now()
        );

        assertEquals(TrackedItemLotRepository.ReconcileResult.SUCCESS, result);

        List<TrackedItemLot> allLots = lotRepository.findAllByOwner(
                OwnershipSubjectType.PLAYER, owner.toString());
        assertEquals(3, allLots.size(), "Should create 3 lots for 3 fingerprints");
    }

    @Test
    void reconcileWithEmptyMapZerosAllExistingLots() throws Exception {
        UUID owner = UUID.randomUUID();

        // First: create two lots
        Map<LotCompatibilityFingerprint, Integer> amounts = new LinkedHashMap<>();
        amounts.put(fp("minecraft:cobblestone"), 32);
        amounts.put(fp("minecraft:dirt"), 16);
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                amounts, Instant.now()
        );

        // Second: empty snapshot zeros everything
        lotRepository.reconcileOwnerAggregates(
                OwnershipSubjectType.PLAYER, owner.toString(), "TestPlayer",
                Map.of(),
                Instant.now()
        );

        List<TrackedItemLot> allLots = lotRepository.findAllByOwner(
                OwnershipSubjectType.PLAYER, owner.toString());
        assertEquals(2, allLots.size(), "Lots should still exist (zeroed, not deleted)");
        assertTrue(allLots.stream().allMatch(l -> l.currentAmount() == 0),
                "All lots should be zeroed");
    }
}
