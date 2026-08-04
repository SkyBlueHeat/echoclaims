package io.github.skyblueheat.echoclaims.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe counters for claim lifecycle operations.
 *
 * <p>All counters are {@link AtomicLong} so they can be safely updated from both the
 * server thread (on command) and the persistence thread (on completion).</p>
 */
public final class ClaimMetrics {

    private final AtomicLong claimsCreated = new AtomicLong();
    private final AtomicLong claimsSubmitted = new AtomicLong();
    private final AtomicLong claimsCancelled = new AtomicLong();
    private final AtomicLong claimsRejected = new AtomicLong();
    private final AtomicLong duplicateOpenClaims = new AtomicLong();
    private final AtomicLong rateLimited = new AtomicLong();
    private final AtomicLong concurrencyConflicts = new AtomicLong();

    public void recordClaimCreated() {
        claimsCreated.incrementAndGet();
    }

    public void recordClaimSubmitted() {
        claimsSubmitted.incrementAndGet();
    }

    public void recordClaimCancelled() {
        claimsCancelled.incrementAndGet();
    }

    public void recordClaimRejected() {
        claimsRejected.incrementAndGet();
    }

    public void recordDuplicateOpenClaim() {
        duplicateOpenClaims.incrementAndGet();
    }

    public void recordRateLimited() {
        rateLimited.incrementAndGet();
    }

    public void recordConcurrencyConflict() {
        concurrencyConflicts.incrementAndGet();
    }

    public long claimsCreated() {
        return claimsCreated.get();
    }

    public long claimsSubmitted() {
        return claimsSubmitted.get();
    }

    public long claimsCancelled() {
        return claimsCancelled.get();
    }

    public long claimsRejected() {
        return claimsRejected.get();
    }

    public long duplicateOpenClaims() {
        return duplicateOpenClaims.get();
    }

    public long rateLimited() {
        return rateLimited.get();
    }

    public long concurrencyConflicts() {
        return concurrencyConflicts.get();
    }

    public ClaimMetricsSnapshot snapshot() {
        return new ClaimMetricsSnapshot(
                claimsCreated.get(),
                claimsSubmitted.get(),
                claimsCancelled.get(),
                claimsRejected.get(),
                duplicateOpenClaims.get(),
                rateLimited.get(),
                concurrencyConflicts.get()
        );
    }

    public record ClaimMetricsSnapshot(
            long claimsCreated,
            long claimsSubmitted,
            long claimsCancelled,
            long claimsRejected,
            long duplicateOpenClaims,
            long rateLimited,
            long concurrencyConflicts
    ) {
    }
}
