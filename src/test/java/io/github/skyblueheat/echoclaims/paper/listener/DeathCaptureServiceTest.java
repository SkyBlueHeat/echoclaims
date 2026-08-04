package io.github.skyblueheat.echoclaims.paper.listener;

import io.github.skyblueheat.echoclaims.application.DeduplicationKeyFactory;
import io.github.skyblueheat.echoclaims.application.EvidenceMetrics;
import io.github.skyblueheat.echoclaims.application.EvidencePersistenceService;
import io.github.skyblueheat.echoclaims.application.ItemValueScorer;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.domain.item.ItemScoreWeights;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import io.github.skyblueheat.echoclaims.integration.IntegrationRegistry;
import io.github.skyblueheat.echoclaims.integration.ItemSerializer;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.EvidenceStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteEvidenceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DeathCaptureService lifecycle and deduplication concurrency.
 *
 * <p>These tests verify the disable flag prevents capture, the service correctly
 * delegates to persistence, and concurrent duplicate captures produce the same
 * deduplication key.</p>
 */
class DeathCaptureServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledServiceDoesNotCapture() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("disable_test.db"));
        database.initialize();
        EvidenceStore store = new SqliteEvidenceStore(database);
        EvidenceMetrics metrics = new EvidenceMetrics();
        AtomicInteger errors = new AtomicInteger();

        EvidencePersistenceService persistence = new EvidencePersistenceService(
                store, metrics, t -> errors.incrementAndGet(), 32, Logger.getLogger("test")
        );

        ItemSerializer serializer = new io.github.skyblueheat.echoclaims.integration.bukkit.BukkitItemSerializer();
        ItemValueScorer scorer = new ItemValueScorer(ItemScoreWeights.defaults());
        IntegrationRegistry integrations = new IntegrationRegistry((id, t) -> {});
        integrations.freeze();

        DeathCaptureService service = new DeathCaptureService(
                EchoClaimsSettings::defaults,
                serializer,
                scorer,
                integrations,
                persistence,
                Logger.getLogger("test")
        );

        service.disable();

        assertEquals(0, metrics.acceptedCaptures(),
                "No captures should have been accepted by a disabled service");

        persistence.shutdown(Duration.ofSeconds(5));
    }

    @Test
    void concurrentDuplicateCapturesProduceSameDeduplicationKey() {
        UUID player = UUID.randomUUID();
        long timestamp = 10_000L;
        String world = "world";
        Coordinates coords = new Coordinates(10, 20, 30);
        String cause = "FALL";

        InventorySnapshot snapshot = new InventorySnapshot(
                UUID.randomUUID(), player,
                io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason.PRE_DEATH,
                timestamp, world, coords, "survival",
                20, 20, 0, 0,
                List.of(new io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem(
                        "0", "minecraft:diamond_sword", 1, "data", "", 42, "Excalibur",
                        10, 1561, java.util.Map.of())),
                List.of(), null, null, 1
        );

        String fingerprint = DeduplicationKeyFactory.snapshotFingerprint(snapshot);

        String key1 = DeduplicationKeyFactory.forPlayerDeath(
                player, timestamp, world, coords, cause, fingerprint
        );
        String key2 = DeduplicationKeyFactory.forPlayerDeath(
                player, timestamp, world, coords, cause, fingerprint
        );

        assertEquals(key1, key2,
                "Concurrent duplicate captures must produce the same deduplication key");
    }

    @Test
    void twoDistinctDeathsSameSecondDifferentCauseProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        long sameSecond = 10_000L;
        String world = "world";
        Coordinates coords = new Coordinates(10, 20, 30);
        String fingerprint = "abc123";

        String key1 = DeduplicationKeyFactory.forPlayerDeath(
                player, sameSecond, world, coords, "FALL", fingerprint
        );
        String key2 = DeduplicationKeyFactory.forPlayerDeath(
                player, sameSecond, world, coords, "ENTITY_ATTACK", fingerprint
        );

        assertTrue(!key1.equals(key2),
                "Two deaths with different causes in the same second must produce different keys");
    }

    @Test
    void twoDistinctDeathsSameSecondSameCauseDifferentInventoryProduceDifferentKeys() {
        UUID player = UUID.randomUUID();
        long sameSecond = 10_000L;
        String world = "world";
        Coordinates coords = new Coordinates(10, 20, 30);
        String cause = "ENTITY_ATTACK";

        InventorySnapshot snapshot1 = new InventorySnapshot(
                UUID.randomUUID(), player,
                io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason.PRE_DEATH,
                sameSecond, world, coords, "survival",
                20, 20, 0, 0,
                List.of(new io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem(
                        "0", "minecraft:diamond_sword", 1, "data", "", 42, "Excalibur",
                        10, 1561, java.util.Map.of())),
                List.of(), null, null, 1
        );

        InventorySnapshot snapshot2 = new InventorySnapshot(
                UUID.randomUUID(), player,
                io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason.PRE_DEATH,
                sameSecond, world, coords, "survival",
                20, 20, 0, 0,
                List.of(new io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem(
                        "0", "minecraft:netherite_sword", 1, "data2", "", 50, "",
                        0, 2031, java.util.Map.of())),
                List.of(), null, null, 1
        );

        String fp1 = DeduplicationKeyFactory.snapshotFingerprint(snapshot1);
        String fp2 = DeduplicationKeyFactory.snapshotFingerprint(snapshot2);

        String key1 = DeduplicationKeyFactory.forPlayerDeath(
                player, sameSecond, world, coords, cause, fp1
        );
        String key2 = DeduplicationKeyFactory.forPlayerDeath(
                player, sameSecond, world, coords, cause, fp2
        );

        assertTrue(!key1.equals(key2),
                "Two deaths with different inventories in the same second must produce different keys");
    }
}
