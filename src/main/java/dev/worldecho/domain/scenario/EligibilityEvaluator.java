package dev.worldecho.domain.scenario;

import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.binding.ContentBinding;
import dev.worldecho.domain.binding.EnrichedContent;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure-Java evaluator that checks a {@link ContentBinding} or {@link EnrichedContent}
 * against an {@link EligibilityProfile} from an {@link EligibilityCatalog}.
 *
 * <p>The evaluator is thread-safe and never mutates its inputs. Diagnostics are produced
 * in deterministic order: profile errors, type errors, missing roles (sorted), missing
 * capabilities (sorted), missing metadata (faction, rank, superior), missing tags (sorted).</p>
 */
public final class EligibilityEvaluator {

    private final EligibilityCatalog catalog;

    public EligibilityEvaluator(EligibilityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public EligibilityCatalog catalog() {
        return catalog;
    }

    /**
     * Evaluates a content binding against a named profile.
     */
    public EligibilityResult evaluate(ContentBinding binding, String profileId) {
        Objects.requireNonNull(binding, "binding");
        return evaluate(binding.key(), binding.type(), binding.roles(), binding.capabilities(),
                binding.optionalFaction().orElse(null),
                binding.optionalRank().orElse(null),
                binding.optionalSuperior().orElse(null),
                binding.tags(),
                profileId);
    }

    /**
     * Evaluates enriched content against a named profile. Provider-supplied roles and
     * capabilities are merged with configured binding values.
     */
    public EligibilityResult evaluate(EnrichedContent enriched, String profileId) {
        Objects.requireNonNull(enriched, "enriched");
        String faction = null;
        String rank = null;
        ContentKey superior = null;
        Set<String> tags = Set.of();
        BindingType type = null;

        if (enriched.optionalBinding().isPresent()) {
            ContentBinding binding = enriched.optionalBinding().get();
            faction = binding.optionalFaction().orElse(null);
            rank = binding.optionalRank().orElse(null);
            superior = binding.optionalSuperior().orElse(null);
            tags = binding.tags();
            type = binding.type();
        }

        return evaluate(enriched.key(), type, enriched.roles(), enriched.capabilities(),
                faction, rank, superior, tags, profileId);
    }

    private EligibilityResult evaluate(
            ContentKey contentKey,
            BindingType bindingType,
            Set<SemanticRole> roles,
            Set<Capability> capabilities,
            String faction,
            String rank,
            ContentKey superior,
            Set<String> tags,
            String profileId
    ) {
        Optional<EligibilityProfile> profileOpt = catalog.find(profileId);
        if (profileOpt.isEmpty()) {
            return new EligibilityResult(
                    profileId, contentKey, bindingType, EligibilityStatus.NOT_ELIGIBLE,
                    Set.of(), Set.of(), Set.of(), Set.of(),
                    List.of(), Set.of(),
                    List.of(new EligibilityDiagnostic(
                            EligibilityDiagnosticCode.PROFILE_NOT_FOUND,
                            profileId, contentKey, "profile",
                            "Unknown profile: " + profileId
                    ))
            );
        }

        EligibilityProfile profile = profileOpt.get();

        if (bindingType != profile.bindingType()) {
            return new EligibilityResult(
                    profile.id(), contentKey, bindingType, EligibilityStatus.NOT_ELIGIBLE,
                    Set.of(), Set.of(), Set.of(), Set.of(),
                    List.of(), Set.of(),
                    List.of(new EligibilityDiagnostic(
                            EligibilityDiagnosticCode.WRONG_BINDING_TYPE,
                            profile.id(), contentKey, "bindingType",
                            "Expected " + profile.bindingType() + " but got " + bindingType
                    ))
            );
        }

        List<EligibilityDiagnostic> diagnostics = new ArrayList<>();
        List<String> missingMetadata = new ArrayList<>();

        Set<SemanticRole> matchedRoles = new LinkedHashSet<>();
        Set<SemanticRole> missingRoles = new TreeSet<>();
        for (SemanticRole required : sortedRoles(profile.requiredRoles())) {
            if (roles.contains(required)) {
                matchedRoles.add(required);
            } else {
                missingRoles.add(required);
                diagnostics.add(new EligibilityDiagnostic(
                        EligibilityDiagnosticCode.MISSING_ROLE,
                        profile.id(), contentKey, required.name(),
                        "Missing role: " + required.name()
                ));
            }
        }

        Set<Capability> matchedCapabilities = new LinkedHashSet<>();
        Set<Capability> missingCapabilities = new TreeSet<>();
        for (Capability required : sortedCapabilities(profile.requiredCapabilities())) {
            if (capabilities.contains(required)) {
                matchedCapabilities.add(required);
            } else {
                missingCapabilities.add(required);
                diagnostics.add(new EligibilityDiagnostic(
                        EligibilityDiagnosticCode.MISSING_CAPABILITY,
                        profile.id(), contentKey, required.name(),
                        "Missing capability: " + required.name()
                ));
            }
        }

        if (profile.requiresFaction() && isBlank(faction)) {
            missingMetadata.add("faction");
            diagnostics.add(new EligibilityDiagnostic(
                    EligibilityDiagnosticCode.MISSING_FACTION,
                    profile.id(), contentKey, "faction",
                    "Missing metadata: faction"
            ));
        }

        if (profile.requiresRank() && isBlank(rank)) {
            missingMetadata.add("rank");
            diagnostics.add(new EligibilityDiagnostic(
                    EligibilityDiagnosticCode.MISSING_RANK,
                    profile.id(), contentKey, "rank",
                    "Missing metadata: rank"
            ));
        }

        if (profile.requiresSuperior() && superior == null) {
            missingMetadata.add("superior");
            diagnostics.add(new EligibilityDiagnostic(
                    EligibilityDiagnosticCode.MISSING_SUPERIOR,
                    profile.id(), contentKey, "superior",
                    "Missing metadata: superior"
            ));
        }

        Set<String> missingTags = new TreeSet<>();
        for (String requiredTag : new TreeSet<>(profile.requiredTags())) {
            if (!tags.contains(requiredTag)) {
                missingTags.add(requiredTag);
                diagnostics.add(new EligibilityDiagnostic(
                        EligibilityDiagnosticCode.MISSING_TAG,
                        profile.id(), contentKey, requiredTag,
                        "Missing tag: " + requiredTag
                ));
            }
        }

        EligibilityStatus status = diagnostics.isEmpty()
                ? EligibilityStatus.ELIGIBLE
                : EligibilityStatus.NOT_ELIGIBLE;

        if (status == EligibilityStatus.ELIGIBLE) {
            diagnostics.add(new EligibilityDiagnostic(
                    EligibilityDiagnosticCode.ELIGIBLE,
                    profile.id(), contentKey, "",
                    "Eligible"
                    ));
        }

        return new EligibilityResult(
                profile.id(), contentKey, bindingType, status,
                matchedRoles, matchedCapabilities,
                missingRoles, missingCapabilities,
                missingMetadata, missingTags,
                diagnostics
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<SemanticRole> sortedRoles(Set<SemanticRole> roles) {
        return new ArrayList<>(new TreeSet<>(roles));
    }

    private static List<Capability> sortedCapabilities(Set<Capability> capabilities) {
        return new ArrayList<>(new TreeSet<>(capabilities));
    }
}
