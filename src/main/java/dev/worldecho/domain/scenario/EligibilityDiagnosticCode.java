package dev.worldecho.domain.scenario;

/**
 * Stable, machine-readable diagnostic codes for eligibility evaluation results.
 *
 * <p>Ordering convention for user-facing output:
 * <ol>
 *   <li>{@link #PROFILE_NOT_FOUND}</li>
 *   <li>{@link #NO_BINDING}</li>
 *   <li>{@link #WRONG_BINDING_TYPE}</li>
 *   <li>{@link #MISSING_ROLE}</li>
 *   <li>{@link #MISSING_CAPABILITY}</li>
 *   <li>{@link #MISSING_FACTION}</li>
 *   <li>{@link #MISSING_RANK}</li>
 *   <li>{@link #MISSING_SUPERIOR}</li>
 *   <li>{@link #MISSING_TAG}</li>
 * </ol>
 */
public enum EligibilityDiagnosticCode {
    ELIGIBLE,
    NO_BINDING,
    PROFILE_NOT_FOUND,
    WRONG_BINDING_TYPE,
    MISSING_ROLE,
    MISSING_CAPABILITY,
    MISSING_FACTION,
    MISSING_RANK,
    MISSING_SUPERIOR,
    MISSING_TAG
}
