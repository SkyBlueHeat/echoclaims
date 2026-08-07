package io.github.skyblueheat.echoclaims.domain.review;

/**
 * The final outcome of a completed review.
 *
 * <p>Maps directly to the terminal claim statuses {@code APPROVED},
 * {@code PARTIALLY_APPROVED}, and {@code REJECTED}. Delivery outcomes are
 * reserved for future sprints and are intentionally excluded.</p>
 */
public enum ReviewOutcome {
    APPROVED,
    PARTIALLY_APPROVED,
    REJECTED
}
