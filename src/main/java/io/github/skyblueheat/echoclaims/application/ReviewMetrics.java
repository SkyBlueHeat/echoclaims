package io.github.skyblueheat.echoclaims.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe counters for review lifecycle operations.
 *
 * <p>All counters are {@link AtomicLong} so they can be safely updated from
 * both the server thread and the persistence thread.</p>
 */
public final class ReviewMetrics {

    private final AtomicLong reviewsStarted = new AtomicLong();
    private final AtomicLong reviewsTakenOver = new AtomicLong();
    private final AtomicLong reviewsApproved = new AtomicLong();
    private final AtomicLong reviewsPartiallyApproved = new AtomicLong();
    private final AtomicLong reviewsRejected = new AtomicLong();
    private final AtomicLong internalNotesAdded = new AtomicLong();
    private final AtomicLong informationRequestsSent = new AtomicLong();
    private final AtomicLong playerResponsesReceived = new AtomicLong();
    private final AtomicLong itemDecisionsCreated = new AtomicLong();
    private final AtomicLong itemDecisionsUpdated = new AtomicLong();
    private final AtomicLong reviewConcurrencyConflicts = new AtomicLong();
    private final AtomicLong reviewRejections = new AtomicLong();
    private final AtomicLong evidenceSelectionSessionsTracked = new AtomicLong();

    public void recordReviewStarted() { reviewsStarted.incrementAndGet(); }
    public void recordReviewTakenOver() { reviewsTakenOver.incrementAndGet(); }
    public void recordReviewApproved() { reviewsApproved.incrementAndGet(); }
    public void recordReviewPartiallyApproved() { reviewsPartiallyApproved.incrementAndGet(); }
    public void recordReviewRejected() { reviewsRejected.incrementAndGet(); }
    public void recordInternalNoteAdded() { internalNotesAdded.incrementAndGet(); }
    public void recordInformationRequestSent() { informationRequestsSent.incrementAndGet(); }
    public void recordPlayerResponseReceived() { playerResponsesReceived.incrementAndGet(); }
    public void recordItemDecisionCreated() { itemDecisionsCreated.incrementAndGet(); }
    public void recordItemDecisionUpdated() { itemDecisionsUpdated.incrementAndGet(); }
    public void recordReviewConcurrencyConflict() { reviewConcurrencyConflicts.incrementAndGet(); }
    public void recordReviewTransitionRejected() { reviewRejections.incrementAndGet(); }

    public long reviewsStarted() { return reviewsStarted.get(); }
    public long reviewsTakenOver() { return reviewsTakenOver.get(); }
    public long reviewsApproved() { return reviewsApproved.get(); }
    public long reviewsPartiallyApproved() { return reviewsPartiallyApproved.get(); }
    public long reviewsRejected() { return reviewsRejected.get(); }
    public long internalNotesAdded() { return internalNotesAdded.get(); }
    public long informationRequestsSent() { return informationRequestsSent.get(); }
    public long playerResponsesReceived() { return playerResponsesReceived.get(); }
    public long itemDecisionsCreated() { return itemDecisionsCreated.get(); }
    public long itemDecisionsUpdated() { return itemDecisionsUpdated.get(); }
    public long reviewConcurrencyConflicts() { return reviewConcurrencyConflicts.get(); }
    public long reviewRejections() { return reviewRejections.get(); }

    public ReviewMetricsSnapshot snapshot() {
        return new ReviewMetricsSnapshot(
                reviewsStarted.get(), reviewsTakenOver.get(),
                reviewsApproved.get(), reviewsPartiallyApproved.get(),
                reviewsRejected.get(), internalNotesAdded.get(),
                informationRequestsSent.get(), playerResponsesReceived.get(),
                itemDecisionsCreated.get(), itemDecisionsUpdated.get(),
                reviewConcurrencyConflicts.get(), reviewRejections.get()
        );
    }

    public record ReviewMetricsSnapshot(
            long reviewsStarted,
            long reviewsTakenOver,
            long reviewsApproved,
            long reviewsPartiallyApproved,
            long reviewsRejected,
            long internalNotesAdded,
            long informationRequestsSent,
            long playerResponsesReceived,
            long itemDecisionsCreated,
            long itemDecisionsUpdated,
            long reviewConcurrencyConflicts,
            long reviewRejections
    ) {}
}
