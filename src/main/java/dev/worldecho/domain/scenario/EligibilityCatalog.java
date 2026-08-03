package dev.worldecho.domain.scenario;

import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.content.Capability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable catalog of built-in {@link EligibilityProfile}s.
 *
 * <p>Profiles are keyed by their lowercased ID. Duplicate IDs are rejected at construction
 * time. The catalog provides deterministic lookup and ordered iteration.</p>
 */
public final class EligibilityCatalog {

    private final Map<String, EligibilityProfile> profilesById;
    private final List<EligibilityProfile> orderedProfiles;

    private EligibilityCatalog(Map<String, EligibilityProfile> profiles) {
        this.profilesById = Map.copyOf(profiles);
        this.orderedProfiles = List.copyOf(profiles.values());
    }

    /**
     * Returns the built-in catalog with all Sprint 0.2B profiles.
     */
    public static EligibilityCatalog builtin() {
        return BuiltinCatalogHolder.INSTANCE;
    }

    /**
     * Creates a catalog from the given profiles. Duplicate IDs (after lowercasing) are
     * rejected.
     */
    public static EligibilityCatalog of(EligibilityProfile... profiles) {
        Map<String, EligibilityProfile> map = new LinkedHashMap<>();
        for (EligibilityProfile profile : profiles) {
            String id = profile.id();
            if (map.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate profile ID: " + id);
            }
            map.put(id, profile);
        }
        return new EligibilityCatalog(map);
    }

    public Optional<EligibilityProfile> find(String profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profilesById.get(profileId.toLowerCase(java.util.Locale.ROOT)));
    }

    public List<EligibilityProfile> profilesFor(BindingType bindingType) {
        return orderedProfiles.stream()
                .filter(p -> p.bindingType() == bindingType)
                .toList();
    }

    public List<EligibilityProfile> allProfiles() {
        return orderedProfiles;
    }

    public Set<String> allIds() {
        return profilesById.keySet();
    }

    public int size() {
        return profilesById.size();
    }

    public int entityProfileCount() {
        return (int) orderedProfiles.stream()
                .filter(p -> p.bindingType() == BindingType.ENTITY)
                .count();
    }

    public int itemProfileCount() {
        return (int) orderedProfiles.stream()
                .filter(p -> p.bindingType() == BindingType.ITEM)
                .count();
    }

    private static final class BuiltinCatalogHolder {
        private static final EligibilityCatalog INSTANCE = createBuiltin();

        private static EligibilityCatalog createBuiltin() {
            return EligibilityCatalog.of(
                    EligibilityProfile.entity("item-carrier")
                            .capabilities(Capability.CAN_HOLD_ITEMS)
                            .description("Entity that may carry or receive a remembered item")
                            .build(),

                    EligibilityProfile.entity("promotion-candidate")
                            .capabilities(Capability.CAN_BE_PROMOTED)
                            .requiresFaction()
                            .requiresRank()
                            .description("Entity that may be promoted to a higher rank")
                            .build(),

                    EligibilityProfile.entity("combat-story-actor")
                            .capabilities(Capability.CAN_FIGHT)
                            .description("Entity that may participate as a combat actor")
                            .build(),

                    EligibilityProfile.item("transferable-story-item")
                            .capabilities(Capability.CAN_CHANGE_OWNER, Capability.CAN_HAVE_HISTORY)
                            .description("Item that may receive persistent ownership history")
                            .build()
            );
        }
    }
}
