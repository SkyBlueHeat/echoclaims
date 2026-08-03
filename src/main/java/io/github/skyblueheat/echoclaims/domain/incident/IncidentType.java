package io.github.skyblueheat.echoclaims.domain.incident;

/**
 * Categorises the kind of event that produced an incident.
 *
 * <p>Only {@code PLAYER_DEATH} is implemented in the MVP-01 sprint. The enum is prepared
 * for future incident types without implementing their capture logic.</p>
 */
public enum IncidentType {
    PLAYER_DEATH
}
