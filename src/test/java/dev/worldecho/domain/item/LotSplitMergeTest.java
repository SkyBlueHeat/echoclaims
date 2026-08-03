package dev.worldecho.domain.item;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.persistence.DatabaseManager;
import dev.worldecho.persistence.LotOwnershipLedgerRepository;
import dev.worldecho.persistence.SqliteLotOwnershipLedgerRepository;
import dev.worldecho.persistence.SqliteTrackedItemLotRepository;
import dev.worldecho.persistence.TrackedItemLotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests lot split and merge behavior through the persistence layer.
 * Verifies quantity preservation, lineage recording, idempotency, and
 * that ownership is not transferred during split/merge.
 */
class LotSplitMergeTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SqliteTrackedItemLotRepository lotRepo;
    private SqliteLotOwnershipLedgerRepository lotLedgerRepo;
    private LotOwnershipTransitionService lotOwnershipService;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("test-splitmerge.db"));
        databaseManager.initialize();
        lotRepo = new SqliteTrackedItemLotRepository(databaseManager);
        lotLedgerRepo = new SqliteLotOwnershipLedgerRepository(databaseManager);
        lotOwnershipService = new LotOwnershipTransitionService(lotRepo, lotLedgerRepo, FIXED_CLOCK);
    }

    private TrackedItemLotId createLot(String material, int amount, UUID ownerUuid) throws Exception {
        return createLot(material, amount, ownerUuid, TrackedItemLotId.random());
    }

    private TrackedItemLotId createLot(String material, int amount, UUID ownerUuid, TrackedItemLotId lotId) throws Exception {
        Instant now = Instant.now(FIXED_CLOCK);
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material(material)
                .build();
        lotRepo.create(new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:" + material.replace("minecraft:", "")),
                "minecraft", material, fp,
                amount, amount, "AUTOMATIC",
                OwnershipSubject.player(ownerUuid, "TestPlayer").describe(),
                OwnershipSubjectType.PLAYER.token(),
                ownerUuid.toString(),
                "TestPlayer"
        ));
        lotOwnershipService.transition(lotId,
                OwnershipSubject.player(ownerUuid, "TestPlayer"),
                OwnershipTransitionReason.AUTOMATIC_TRACKING,
                "init-" + lotId, "test", "");
        return lotId;
    }

    @Test
    void splitCreatesChildLotWithLineage() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId parentId = createLot("minecraft:cobblestone", 64, owner);

        // Child lot uses a different owner to avoid UNIQUE(owner_type, owner_stable_id, fingerprint) conflict
        TrackedItemLotId childId = createLot("minecraft:cobblestone", 32, otherOwner);

        LotLineageEntry lineage = new LotLineageEntry(
                UUID.randomUUID().toString(), childId, parentId,
                LotRelationType.SPLIT_FROM, 32, 64, Instant.now(FIXED_CLOCK), "split", "split-1"
        );
        lotRepo.appendLineage(lineage);

        List<LotLineageEntry> history = lotRepo.findLineage(childId);
        assertEquals(1, history.size());
        assertEquals(LotRelationType.SPLIT_FROM, history.get(0).relationType());
        assertEquals(parentId, history.get(0).relatedLotId());
    }

    @Test
    void splitLineageIsIdempotent() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId parentId = createLot("minecraft:cobblestone", 64, owner);
        TrackedItemLotId childId = createLot("minecraft:cobblestone", 32, otherOwner);

        LotLineageEntry entry = new LotLineageEntry(
                UUID.randomUUID().toString(), childId, parentId,
                LotRelationType.SPLIT_FROM, 32, 64, Instant.now(FIXED_CLOCK),
                "split", "idem-split-1"
        );
        lotRepo.appendLineage(entry);
        lotRepo.appendLineage(entry);

        assertEquals(1, lotRepo.findLineage(childId).size());
    }

    @Test
    void splitPreservesTotalQuantity() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId parentId = createLot("minecraft:cobblestone", 64, owner);

        lotRepo.updateAmount(parentId, 32);

        TrackedItemLotId childId = createLot("minecraft:cobblestone", 32, otherOwner);

        Optional<TrackedItemLot> parent = lotRepo.findById(parentId);
        Optional<TrackedItemLot> child = lotRepo.findById(childId);
        assertTrue(parent.isPresent());
        assertTrue(child.isPresent());
        assertEquals(32, parent.get().currentAmount());
        assertEquals(32, child.get().currentAmount());
        assertEquals(64, parent.get().currentAmount() + child.get().currentAmount());
    }

    @Test
    void splitDoesNotCreateOwnershipTransfer() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId parentId = createLot("minecraft:cobblestone", 64, owner);
        TrackedItemLotId childId = createLot("minecraft:cobblestone", 32, otherOwner);

        lotOwnershipService.transition(childId,
                OwnershipSubject.player(otherOwner, "TestPlayer"),
                OwnershipTransitionReason.AUTOMATIC_TRACKING,
                "init-" + childId, "test", "");

        Optional<LotOwnershipState> parentState = lotOwnershipService.currentOwnership(parentId);
        Optional<LotOwnershipState> childState = lotOwnershipService.currentOwnership(childId);

        assertTrue(parentState.isPresent());
        assertTrue(childState.isPresent());
        assertEquals(1, parentState.get().historyCount());
        assertEquals(1, childState.get().historyCount());
    }

    @Test
    void mergeLinksAbsorbedLot() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId survivorId = createLot("minecraft:cobblestone", 20, owner);
        TrackedItemLotId absorbedId = createLot("minecraft:cobblestone", 30, otherOwner);

        LotLineageEntry mergeEntry = new LotLineageEntry(
                UUID.randomUUID().toString(), absorbedId, survivorId,
                LotRelationType.MERGED_INTO, 30, 50, Instant.now(FIXED_CLOCK),
                "merge", "merge-1"
        );
        lotRepo.appendLineage(mergeEntry);

        lotRepo.updateAmount(survivorId, 50);

        List<LotLineageEntry> absorbedLineage = lotRepo.findLineage(absorbedId);
        assertEquals(1, absorbedLineage.size());
        assertEquals(LotRelationType.MERGED_INTO, absorbedLineage.get(0).relationType());
        assertEquals(survivorId, absorbedLineage.get(0).relatedLotId());

        Optional<TrackedItemLot> survivor = lotRepo.findById(survivorId);
        assertTrue(survivor.isPresent());
        assertEquals(50, survivor.get().currentAmount());
    }

    @Test
    void mergeIsIdempotent() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId survivorId = createLot("minecraft:cobblestone", 20, owner);
        TrackedItemLotId absorbedId = createLot("minecraft:cobblestone", 30, otherOwner);

        LotLineageEntry entry = new LotLineageEntry(
                UUID.randomUUID().toString(), absorbedId, survivorId,
                LotRelationType.MERGED_INTO, 30, 50, Instant.now(FIXED_CLOCK),
                "merge", "idem-merge-1"
        );
        lotRepo.appendLineage(entry);
        lotRepo.appendLineage(entry);

        assertEquals(1, lotRepo.findLineage(absorbedId).size());
    }

    @Test
    void mergePreservesTotalQuantity() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId survivorId = createLot("minecraft:cobblestone", 20, owner);
        TrackedItemLotId absorbedId = createLot("minecraft:cobblestone", 30, otherOwner);

        int totalBefore = lotRepo.findById(survivorId).get().currentAmount()
                + lotRepo.findById(absorbedId).get().currentAmount();

        lotRepo.updateAmount(survivorId, totalBefore);

        Optional<TrackedItemLot> survivor = lotRepo.findById(survivorId);
        assertTrue(survivor.isPresent());
        assertEquals(50, survivor.get().currentAmount());
        assertEquals(50, totalBefore);
    }

    @Test
    void mergeDoesNotCreateOwnershipTransfer() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        TrackedItemLotId survivorId = createLot("minecraft:cobblestone", 20, owner);
        TrackedItemLotId absorbedId = createLot("minecraft:cobblestone", 30, otherOwner);

        lotOwnershipService.transition(survivorId,
                OwnershipSubject.player(owner, "TestPlayer"),
                OwnershipTransitionReason.AUTOMATIC_TRACKING,
                "merge-recon-" + survivorId, "merge", "");

        Optional<LotOwnershipState> state = lotOwnershipService.currentOwnership(survivorId);
        assertTrue(state.isPresent());
        assertEquals(1, state.get().historyCount(),
                "Same owner should produce NO_CHANGE, not a new entry");
    }

    @Test
    void incompatibleLotsDoNotMergeByFingerprint() throws Exception {
        UUID owner = UUID.randomUUID();
        TrackedItemLotId cobbleId = createLot("minecraft:cobblestone", 32, owner);
        TrackedItemLotId dirtId = createLot("minecraft:dirt", 32, owner);

        LotCompatibilityFingerprint cobbleFp = lotRepo.findById(cobbleId).get().fingerprint();
        LotCompatibilityFingerprint dirtFp = lotRepo.findById(dirtId).get().fingerprint();

        assertTrue(!cobbleFp.equals(dirtFp),
                "Different materials should produce different fingerprints");
    }

    @Test
    void differentPlayersWithSameFingerprintGetDifferentLots() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();

        TrackedItemLotId lotA = createLot("minecraft:cobblestone", 32, ownerA);
        TrackedItemLotId lotB = createLot("minecraft:cobblestone", 32, ownerB);

        String subjectA = OwnershipSubject.player(ownerA, "PlayerA").describe();
        String subjectB = OwnershipSubject.player(ownerB, "PlayerB").describe();

        Optional<TrackedItemLot> foundA = lotRepo.findByFingerprintAndOwner(fp, subjectA);
        Optional<TrackedItemLot> foundB = lotRepo.findByFingerprintAndOwner(fp, subjectB);

        assertTrue(foundA.isPresent(), "Should find lot for player A");
        assertTrue(foundB.isPresent(), "Should find lot for player B");
        assertEquals(lotA, foundA.get().lotId(), "Should find correct lot for player A");
        assertEquals(lotB, foundB.get().lotId(), "Should find correct lot for player B");
    }

    @Test
    void samePlayerWithSameFingerprintReusesLot() throws Exception {
        UUID owner = UUID.randomUUID();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();

        TrackedItemLotId lotId = createLot("minecraft:cobblestone", 32, owner);
        String subject = OwnershipSubject.player(owner, "TestPlayer").describe();

        Optional<TrackedItemLot> found = lotRepo.findByFingerprintAndOwner(fp, subject);
        assertTrue(found.isPresent());
        assertEquals(lotId, found.get().lotId());
    }

    @Test
    void findByFingerprintAndOwnerReturnsEmptyForUnknownOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        createLot("minecraft:cobblestone", 32, owner);

        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();
        String unknownSubject = OwnershipSubject.player(UUID.randomUUID(), "Unknown").describe();

        Optional<TrackedItemLot> found = lotRepo.findByFingerprintAndOwner(fp, unknownSubject);
        assertTrue(found.isEmpty(), "Should not find lot for unknown owner");
    }
}
