package dev.worldecho.domain.item;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable machine-readable reason for an ownership transition.
 *
 * <p>Reasons are vocabulary for persisted history and future integrations.
 * Not all reasons have listeners in Sprint 0.3A.
 */
public enum OwnershipTransitionReason {
    TRACKED,
    ADMIN_ASSIGNMENT,
    PLAYER_HELD,
    ENTITY_HELD,
    DROPPED,
    PICKED_UP,
    STORED,
    RETRIEVED,
    TRANSFERRED,
    RECOVERED,
    INVENTORY_RECONCILIATION,
    INVENTORY_TRANSFER,
    AUTOMATIC_TRACKING,
    UNKNOWN;

    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OwnershipTransitionReason fromToken(String token) {
        Objects.requireNonNull(token, "token");
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (OwnershipTransitionReason reason : values()) {
            if (reason.token().equals(normalized)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown transition reason: " + token);
    }
}
