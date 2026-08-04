package io.github.skyblueheat.echoclaims.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRateLimitServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void allowsFirstClaim() {
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ofSeconds(30));
        assertTrue(service.check(PLAYER).isAllowed());
    }

    @Test
    void blocksSecondClaimWithinCooldown() {
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ofSeconds(30));
        service.recordCreation(PLAYER);
        assertFalse(service.check(PLAYER).isAllowed());
    }

    @Test
    void zeroCooldownDisablesRateLimiting() {
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ZERO);
        service.recordCreation(PLAYER);
        assertTrue(service.check(PLAYER).isAllowed());
    }

    @Test
    void negativeCooldownDisablesRateLimiting() {
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ofSeconds(-1));
        service.recordCreation(PLAYER);
        assertTrue(service.check(PLAYER).isAllowed());
    }

    @Test
    void differentPlayersAreIndependent() {
        UUID other = UUID.randomUUID();
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ofSeconds(30));
        service.recordCreation(PLAYER);
        assertFalse(service.check(PLAYER).isAllowed());
        assertTrue(service.check(other).isAllowed());
    }

    @Test
    void remainingMillisIsPositiveWhenLimited() {
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ofSeconds(60));
        service.recordCreation(PLAYER);
        var result = service.check(PLAYER);
        assertFalse(result.isAllowed());
        assertTrue(result.remainingMillis() > 0);
    }

    @Test
    void cleanupExpiredEntriesPreventsUnboundedGrowth() {
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ofMillis(50));
        for (int i = 0; i < 100; i++) {
            service.recordCreation(UUID.randomUUID());
        }
        assertEquals(100, service.trackedPlayerCount());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        service.check(UUID.randomUUID());
        assertTrue(service.trackedPlayerCount() < 100,
                "Expired entries should be cleaned up, got: " + service.trackedPlayerCount());
    }

    @Test
    void restartResetsRateLimitState() {
        ClaimRateLimitService first = new ClaimRateLimitService(Duration.ofSeconds(30));
        first.recordCreation(PLAYER);
        assertFalse(first.check(PLAYER).isAllowed());

        ClaimRateLimitService second = new ClaimRateLimitService(Duration.ofSeconds(30));
        assertTrue(second.check(PLAYER).isAllowed(),
                "New instance should not carry state from previous instance");
    }

    @Test
    void concurrentChecksAreSafe() throws Exception {
        ClaimRateLimitService service = new ClaimRateLimitService(Duration.ofSeconds(30));
        int threads = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final UUID uuid = UUID.randomUUID();
            exec.submit(() -> {
                service.check(uuid);
                service.recordCreation(uuid);
                service.check(uuid);
                latch.countDown();
            });
        }
        latch.await();
        exec.shutdown();
        assertEquals(threads, service.trackedPlayerCount());
    }
}
