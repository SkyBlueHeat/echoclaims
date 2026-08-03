package dev.worldecho.domain.item;

/**
 * Status of an ownership transition attempt.
 */
public enum OwnershipResultStatus {
    RECORDED,
    IDEMPOTENT_REPLAY,
    NO_CHANGE,
    ITEM_NOT_TRACKED,
    INVALID_SUBJECT,
    CONFLICT,
    PERSISTENCE_FAILURE
}
