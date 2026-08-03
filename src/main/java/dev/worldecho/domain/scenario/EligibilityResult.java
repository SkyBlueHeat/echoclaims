package dev.worldecho.domain.scenario;

import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable result of evaluating a content target against an eligibility profile.
 *
 * @param profileId           the profile ID that was evaluated
 * @param contentKey          the content key that was evaluated
 * @param bindingType         the binding type of the target, or {@code null} when no binding exists
 * @param status              eligible or not eligible
 * @param matchedRoles        roles that satisfied the profile requirements
 * @param matchedCapabilities capabilities that satisfied the profile requirements
 * @param missingRoles        roles required by the profile but absent from the target
 * @param missingCapabilities capabilities required by the profile but absent from the target
 * @param missingMetadata     metadata fields required but absent (e.g. "faction", "rank", "superior")
 * @param missingTags         tags required but absent
 * @param diagnostics         structured diagnostics in deterministic order
 */
public record EligibilityResult(
        String profileId,
        ContentKey contentKey,
        BindingType bindingType,
        EligibilityStatus status,
        Set<SemanticRole> matchedRoles,
        Set<Capability> matchedCapabilities,
        Set<SemanticRole> missingRoles,
        Set<Capability> missingCapabilities,
        List<String> missingMetadata,
        Set<String> missingTags,
        List<EligibilityDiagnostic> diagnostics
) {
    public EligibilityResult {
        profileId = Objects.requireNonNullElse(profileId, "");
        bindingType = Objects.requireNonNullElse(bindingType, null);
        Objects.requireNonNull(status, "status");
        matchedRoles = Set.copyOf(Objects.requireNonNullElse(matchedRoles, Set.of()));
        matchedCapabilities = Set.copyOf(Objects.requireNonNullElse(matchedCapabilities, Set.of()));
        missingRoles = Set.copyOf(Objects.requireNonNullElse(missingRoles, Set.of()));
        missingCapabilities = Set.copyOf(Objects.requireNonNullElse(missingCapabilities, Set.of()));
        missingMetadata = List.copyOf(Objects.requireNonNullElse(missingMetadata, List.of()));
        missingTags = Set.copyOf(Objects.requireNonNullElse(missingTags, Set.of()));
        diagnostics = List.copyOf(Objects.requireNonNullElse(diagnostics, List.of()));
    }

    public boolean eligible() {
        return status == EligibilityStatus.ELIGIBLE;
    }
}
