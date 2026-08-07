package io.github.skyblueheat.echoclaims.domain.review;

/**
 * The outcome of a single item-level review decision.
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>{@code APPROVED} requires approved quantity equal to original quantity</li>
 *   <li>{@code PARTIALLY_APPROVED} requires approved quantity greater than zero
 *       and less than original quantity</li>
 *   <li>{@code REJECTED} requires approved quantity equal to zero</li>
 * </ul>
 */
public enum ReviewItemOutcome {
    APPROVED,
    PARTIALLY_APPROVED,
    REJECTED
}
