package io.github.skyblueheat.echoclaims.application;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for claim creation.
 *
 * <p>Tracks the timestamp of each player's last claim creation and rejects new
 * creations that arrive within the configured cooldown. The limiter is thread-safe
 * and does not touch the database.</p>
 *
 * <p>Admins can tune the cooldown through configuration. A cooldown of zero disables
 * rate limiting entirely.</p>
 */
public final class ClaimRateLimitService {

    private final Duration cooldown;
    private final Map<UUID, Long> lastClaimTimes = new ConcurrentHashMap<>();

    public ClaimRateLimitService(Duration cooldown) {
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Checks whether the player is allowed to create a claim now.
     *
     * <p>As a side effect, expired entries from other players are opportunistically
     * removed so the internal map cannot grow unbounded with unique player UUIDs.</p>
     *
     * @return a result indicating whether the action is allowed or rate-limited
     */
    public RateLimitResult check(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        if (cooldown.isZero() || cooldown.isNegative()) {
            return RateLimitResult.allowed();
        }

        long now = System.currentTimeMillis();
        cleanupExpired(now);

        Long lastTime = lastClaimTimes.get(playerUuid);
        if (lastTime == null) {
            return RateLimitResult.allowed();
        }

        long elapsed = now - lastTime;
        long remaining = cooldown.toMillis() - elapsed;
        if (remaining > 0) {
            return RateLimitResult.limited(remaining);
        }

        return RateLimitResult.allowed();
    }

    /**
     * Records that a claim was created by the player, starting the cooldown window.
     */
    public void recordCreation(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (!cooldown.isZero() && !cooldown.isNegative()) {
            lastClaimTimes.put(playerUuid, System.currentTimeMillis());
        }
    }

    /**
     * Returns the configured cooldown duration.
     */
    public Duration cooldown() {
        return cooldown;
    }

    /**
     * Returns the current number of tracked player entries.
     */
    public int trackedPlayerCount() {
        return lastClaimTimes.size();
    }

    private void cleanupExpired(long now) {
        long cooldownMillis = cooldown.toMillis();
        var iterator = lastClaimTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue() > cooldownMillis) {
                iterator.remove();
            }
        }
    }

    public static final class RateLimitResult {
        private final boolean allowed;
        private final long remainingMillis;

        private RateLimitResult(boolean allowed, long remainingMillis) {
            this.allowed = allowed;
            this.remainingMillis = remainingMillis;
        }

        static RateLimitResult allowed() {
            return new RateLimitResult(true, 0);
        }

        static RateLimitResult limited(long remainingMillis) {
            return new RateLimitResult(false, remainingMillis);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long remainingMillis() {
            return remainingMillis;
        }
    }
}
