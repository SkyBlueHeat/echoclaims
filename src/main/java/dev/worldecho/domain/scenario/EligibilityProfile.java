package dev.worldecho.domain.scenario;

import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable requirement description for a diagnostic profile.
 *
 * <p>Profiles are <em>not</em> executable scenarios. They describe what semantic
 * metadata a content binding must have to be considered ready for a future story role.</p>
 *
 * @param id                  stable, unique profile identifier
 * @param bindingType         whether this profile applies to entities or items
 * @param requiredRoles       roles that must be present
 * @param requiredCapabilities capabilities that must be present
 * @param requiresFaction     whether faction metadata is required
 * @param requiresRank        whether rank metadata is required
 * @param requiresSuperior    whether superior metadata is required
 * @param requiredTags        tags that must all be present
 * @param description         short human-readable summary
 */
public record EligibilityProfile(
        String id,
        BindingType bindingType,
        Set<SemanticRole> requiredRoles,
        Set<Capability> requiredCapabilities,
        boolean requiresFaction,
        boolean requiresRank,
        boolean requiresSuperior,
        Set<String> requiredTags,
        String description
) {
    public EligibilityProfile {
        Objects.requireNonNull(id, "id");
        id = id.toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(bindingType, "bindingType");
        requiredRoles = Set.copyOf(Objects.requireNonNullElse(requiredRoles, Set.of()));
        requiredCapabilities = Set.copyOf(Objects.requireNonNullElse(requiredCapabilities, Set.of()));
        requiredTags = Set.copyOf(Objects.requireNonNullElse(requiredTags, Set.of()));
        description = Objects.requireNonNullElse(description, "");
    }

    public static Builder entity(String id) {
        return new Builder(id, BindingType.ENTITY);
    }

    public static Builder item(String id) {
        return new Builder(id, BindingType.ITEM);
    }

    public static final class Builder {
        private final String id;
        private final BindingType bindingType;
        private Set<SemanticRole> requiredRoles = Set.of();
        private Set<Capability> requiredCapabilities = Set.of();
        private boolean requiresFaction;
        private boolean requiresRank;
        private boolean requiresSuperior;
        private Set<String> requiredTags = Set.of();
        private String description = "";

        private Builder(String id, BindingType bindingType) {
            this.id = id;
            this.bindingType = bindingType;
        }

        public Builder roles(SemanticRole... roles) {
            this.requiredRoles = Set.of(roles);
            return this;
        }

        public Builder capabilities(Capability... capabilities) {
            this.requiredCapabilities = Set.of(capabilities);
            return this;
        }

        public Builder requiresFaction() {
            this.requiresFaction = true;
            return this;
        }

        public Builder requiresRank() {
            this.requiresRank = true;
            return this;
        }

        public Builder requiresSuperior() {
            this.requiresSuperior = true;
            return this;
        }

        public Builder tags(String... tags) {
            this.requiredTags = Set.of(tags);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public EligibilityProfile build() {
            return new EligibilityProfile(
                    id, bindingType, requiredRoles, requiredCapabilities,
                    requiresFaction, requiresRank, requiresSuperior,
                    requiredTags, description
            );
        }
    }
}
