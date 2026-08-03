package dev.worldecho.domain.scenario;

import dev.worldecho.domain.content.ContentKey;

import java.util.Objects;

/**
 * A single structured diagnostic produced during eligibility evaluation.
 *
 * @param code         stable machine-readable code
 * @param profileId    the profile being evaluated, or {@code null} when the profile itself is unknown
 * @param contentKey   the content key being evaluated, or {@code null} when no binding was found
 * @param requirement  the specific field or requirement that failed (e.g. {@code "CAN_FIGHT"}, {@code "faction"})
 * @param explanation  human-readable explanation
 */
public record EligibilityDiagnostic(
        EligibilityDiagnosticCode code,
        String profileId,
        ContentKey contentKey,
        String requirement,
        String explanation
) {
    public EligibilityDiagnostic {
        Objects.requireNonNull(code, "code");
        profileId = Objects.requireNonNullElse(profileId, "");
        contentKey = Objects.requireNonNullElse(contentKey, null);
        requirement = Objects.requireNonNullElse(requirement, "");
        explanation = Objects.requireNonNullElse(explanation, "");
    }

    public boolean isError() {
        return code != EligibilityDiagnosticCode.ELIGIBLE;
    }
}
