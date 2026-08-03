package dev.worldecho.persistence;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.item.LotCompatibilityFingerprint;
import dev.worldecho.domain.item.LotLineageEntry;
import dev.worldecho.domain.item.LotOwnershipLedgerEntry;
import dev.worldecho.domain.item.LotOwnershipState;
import dev.worldecho.domain.item.LotRelationType;
import dev.worldecho.domain.item.OwnershipSubject;
import dev.worldecho.domain.item.OwnershipTransitionReason;
import dev.worldecho.domain.item.TrackedItemLot;
import dev.worldecho.domain.item.TrackedItemLotId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrackedItemLotRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SqliteTrackedItemLotRepository lotRepository;
    private SqliteLotOwnershipLedgerRepository lotLedgerRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("test-lot.db"));
        databaseManager.initialize();
        lotRepository = new SqliteTrackedItemLotRepository(databaseManager);
        lotLedgerRepository = new SqliteLotOwnershipLedgerRepository(databaseManager);
    }

    @Test
    void createAndFindById() throws Exception {
        TrackedItemLotId lotId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();
        TrackedItemLot lot = new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:cobblestone"),
                "minecraft", "minecraft:cobblestone", fp,
                32, 32, "AUTOMATIC", "player:abc",
                "player", "abc", ""
        );

        assertEquals(TrackedItemLotRepository.CreateResult.CREATED, lotRepository.create(lot));
        Optional<TrackedItemLot> found = lotRepository.findById(lotId);
        assertTrue(found.isPresent());
        assertEquals(lotId, found.get().lotId());
        assertEquals(32, found.get().currentAmount());
        assertEquals(fp.serialize(), found.get().fingerprint().serialize());
    }

    @Test
    void createDuplicateReturnsAlreadyExists() throws Exception {
        TrackedItemLotId lotId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .material("minecraft:dirt").build();
        TrackedItemLot lot = new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:dirt"),
                "minecraft", "minecraft:dirt", fp,
                16, 16, "AUTOMATIC", "",
                "", "", ""
        );

        lotRepository.create(lot);
        assertEquals(TrackedItemLotRepository.CreateResult.ALREADY_EXISTS, lotRepository.create(lot));
    }

    @Test
    void findByFingerprint() throws Exception {
        TrackedItemLotId lotId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:iron_ingot")
                .build();
        TrackedItemLot lot = new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:iron_ingot"),
                "minecraft", "minecraft:iron_ingot", fp,
                64, 64, "AUTOMATIC", "",
                "", "", ""
        );
        lotRepository.create(lot);

        Optional<TrackedItemLot> found = lotRepository.findByFingerprint(fp);
        assertTrue(found.isPresent());
        assertEquals(lotId, found.get().lotId());
    }

    @Test
    void updateAmount() throws Exception {
        TrackedItemLotId lotId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone").build();
        TrackedItemLot lot = new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:cobblestone"),
                "minecraft", "minecraft:cobblestone", fp,
                32, 32, "AUTOMATIC", "",
                "", "", ""
        );
        lotRepository.create(lot);

        lotRepository.updateAmount(lotId, 48);
        Optional<TrackedItemLot> found = lotRepository.findById(lotId);
        assertTrue(found.isPresent());
        assertEquals(48, found.get().currentAmount());
    }

    @Test
    void appendAndFindLineage() throws Exception {
        TrackedItemLotId lotId = TrackedItemLotId.random();
        TrackedItemLotId relatedId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone").build();

        lotRepository.create(new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:cobblestone"),
                "minecraft", "minecraft:cobblestone", fp,
                32, 32, "AUTOMATIC", "",
                "", "", ""));
        lotRepository.create(new TrackedItemLot(
                relatedId, now, now, now,
                ContentKey.parse("minecraft:cobblestone"),
                "minecraft", "minecraft:cobblestone", fp,
                16, 16, "AUTOMATIC", "",
                "", "", ""));

        LotLineageEntry entry = new LotLineageEntry(
                UUID.randomUUID().toString(), lotId, relatedId,
                LotRelationType.SPLIT_FROM, 32, 16, now, "test", "key-1"
        );
        lotRepository.appendLineage(entry);

        var lineage = lotRepository.findLineage(lotId);
        assertEquals(1, lineage.size());
        assertEquals(LotRelationType.SPLIT_FROM, lineage.get(0).relationType());
    }

    @Test
    void lotOwnershipLedgerAppendAndFind() throws Exception {
        TrackedItemLotId lotId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone").build();
        lotRepository.create(new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:cobblestone"),
                "minecraft", "minecraft:cobblestone", fp,
                32, 32, "AUTOMATIC", "",
                "", "", ""));

        OwnershipSubject player = OwnershipSubject.player(UUID.randomUUID(), "TestPlayer");
        LotOwnershipLedgerEntry entry = new LotOwnershipLedgerEntry(
                UUID.randomUUID().toString(), lotId, 1,
                null, player, OwnershipTransitionReason.AUTOMATIC_TRACKING,
                now, now, "test", "", "key-1", ""
        );

        assertEquals(LotOwnershipLedgerRepository.AppendResult.APPENDED,
                lotLedgerRepository.append(entry));

        Optional<LotOwnershipState> state = lotLedgerRepository.findCurrentOwnership(lotId);
        assertTrue(state.isPresent());
        assertEquals(player, state.get().currentSubject());
        assertEquals(1, state.get().latestSequence());
        assertEquals(1, state.get().historyCount());
    }

    @Test
    void lotOwnershipIdempotencyReplay() throws Exception {
        TrackedItemLotId lotId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone").build();
        lotRepository.create(new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:cobblestone"),
                "minecraft", "minecraft:cobblestone", fp,
                32, 32, "AUTOMATIC", "",
                "", "", ""));

        OwnershipSubject player = OwnershipSubject.player(UUID.randomUUID());
        LotOwnershipLedgerEntry entry = new LotOwnershipLedgerEntry(
                UUID.randomUUID().toString(), lotId, 1,
                null, player, OwnershipTransitionReason.AUTOMATIC_TRACKING,
                now, now, "test", "", "idem-1", ""
        );

        lotLedgerRepository.append(entry);
        assertEquals(LotOwnershipLedgerRepository.AppendResult.IDEMPOTENT_REPLAY,
                lotLedgerRepository.append(entry));

        Optional<LotOwnershipLedgerEntry> found = lotLedgerRepository.findByIdempotencyKey(lotId, "idem-1");
        assertTrue(found.isPresent());
    }

    @Test
    void countAllLots() throws Exception {
        assertEquals(0, lotRepository.count());
        TrackedItemLotId lotId = TrackedItemLotId.random();
        Instant now = Instant.now();
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone").build();
        lotRepository.create(new TrackedItemLot(
                lotId, now, now, now,
                ContentKey.parse("minecraft:cobblestone"),
                "minecraft", "minecraft:cobblestone", fp,
                32, 32, "AUTOMATIC", "",
                "", "", ""));
        assertEquals(1, lotRepository.count());
    }
}
