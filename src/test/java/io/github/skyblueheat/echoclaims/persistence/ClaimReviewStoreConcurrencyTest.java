package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies optimistic concurrency on {@link SqliteClaimReviewStore}:
 * exactly one of N concurrent takeovers succeeds, the rest fail with version mismatch.
 */
class ClaimReviewStoreConcurrencyTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private ClaimReviewRepository reviewRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("review_concurrency.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        reviewRepository = new SqliteClaimReviewRepository(databaseManager);
    }

    private Claim seedClaim() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        new SqliteInventorySnapshotRepository(databaseManager).insert(new InventorySnapshot(
                snapshotId, playerUuid, CaptureReason.PRE_DEATH,
                10_000L, "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        ));
        UUID incidentId = UUID.randomUUID();
        new SqliteIncidentRepository(databaseManager).insert(new Incident(
                incidentId, IncidentType.PLAYER_DEATH, playerUuid,
                10_000L, "world", new Coordinates(0, 0, 0),
                "FALL", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), "dedup-" + UUID.randomUUID()
        ));
        UUID claimId = UUID.randomUUID();
        Claim claim = new Claim(
                claimId, "REF" + claimId.toString().substring(0, 8).toUpperCase(),
                incidentId, playerUuid, ClaimStatus.SUBMITTED, ClaimSource.PLAYER_COMMAND,
                "desc", 10_000L, 10_000L, 0L, 0, Map.of()
        );
        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER,
                claim.playerUuid(), ClaimAction.CREATED, "",
                System.currentTimeMillis(), 0, Map.of()
        ));
        return claim;
    }

    private ClaimAuditEntry auditEntry(UUID claimId, ClaimAction action, int version) {
        return new ClaimAuditEntry(
                UUID.randomUUID(), claimId, ClaimActorType.STAFF,
                UUID.randomUUID(), action, "test",
                System.currentTimeMillis(), version, Map.of()
        );
    }

    @Test
    void concurrentTakeoversExactlyOneSucceeds() throws Exception {
        Claim claim = seedClaim();
        UUID initialStaff = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), initialStaff,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final UUID newReviewer = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startGate.await();
                    boolean ok = reviewStore.takeoverReview(
                            review.id(), newReviewer, 0,
                            auditEntry(claim.id(), ClaimAction.REVIEW_TAKEN_OVER, 1));
                    if (ok) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one takeover should succeed");
        assertEquals(threadCount - 1, failures.get(), "All others should fail");
    }

    @Test
    void concurrentFinalizationsExactlyOneSucceeds() throws Exception {
        Claim claim = seedClaim();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                UUID.randomUUID(), claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(),
                auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));

        Optional<Claim> underReview = claimStore.findClaimById(claim.id());
        assertTrue(underReview.isPresent());

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    boolean ok = reviewStore.finalizeReview(
                            review.id(),
                            io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome.REJECTED,
                            "Rejected", now, 0,
                            claim.id(), ClaimStatus.REJECTED,
                            underReview.get().version(),
                            null,
                            auditEntry(claim.id(), ClaimAction.REVIEW_REJECTED, 2));
                    if (ok) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one finalization should succeed");
        assertEquals(threadCount - 1, failures.get(), "All others should fail");
    }

    @Test
    void concurrentStartReviewExactlyOneSucceeds() throws Exception {
        Claim claim = seedClaim();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        List<SQLException> errors = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final UUID staffUuid = UUID.randomUUID();
            final ClaimReview review = new ClaimReview(
                    UUID.randomUUID(), claim.id(), staffUuid,
                    ReviewState.OPEN, null, "", System.currentTimeMillis(),
                    System.currentTimeMillis(), 0L, 0, Map.of()
            );
            executor.submit(() -> {
                try {
                    startGate.await();
                    boolean ok = reviewStore.startReview(review, claim.id(), 0,
                            auditEntry(claim.id(), ClaimAction.REVIEW_STARTED, 1));
                    if (ok) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (SQLException e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        int totalSuccess = successes.get();
        int totalFail = failures.get();
        assertTrue(totalSuccess >= 1, "At least one startReview should succeed");
        assertTrue(totalSuccess + totalFail == threadCount,
                "All threads should have completed");
        assertEquals(1, reviewRepository.count(), "Exactly one review should be persisted");
    }
}
