package io.github.skyblueheat.echoclaims.domain.incident;

/**
 * Lifecycle state of an incident.
 *
 * <p>Every incident starts as {@code OPEN}. The MVP-01 sprint does not implement
 * claim approval or refund workflow, so statuses beyond {@code OPEN} are reserved for
 * future sprints.</p>
 */
public enum IncidentStatus {
    OPEN,
    RESOLVED,
    REJECTED
}
