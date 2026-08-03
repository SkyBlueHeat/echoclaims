package dev.worldecho.domain.binding;

import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.content.SemanticRole;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The result of enriching provider-identified content with server-owner configured bindings.
 *
 * <p>Provider-supplied roles and capabilities are merged with configured values; the
 * original {@link IdentifiedContent} is never mutated. Optional binding metadata
 * (faction, rank, superior, tags) is carried alongside but does not alter the base
 * identity.</p>
 */
public record EnrichedContent(
        ContentKey key,
        String displayName,
        Set<SemanticRole> roles,
        Set<Capability> capabilities,
        ContentBinding binding
) {

    public EnrichedContent {
        Objects.requireNonNull(key, "key");
        displayName = Objects.requireNonNullElse(displayName, key.toString());
        roles = Set.copyOf(Objects.requireNonNullElse(roles, Set.of()));
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
    }

    /**
     * Creates an enriched result by merging provider-supplied roles and capabilities
     * with those from the binding. Binding values are added; existing provider values
     * are preserved.
     */
    public static EnrichedContent of(IdentifiedContent base, ContentBinding binding) {
        Objects.requireNonNull(base, "base");

        if (binding == null) {
            return new EnrichedContent(
                    base.key(),
                    base.displayName(),
                    base.roles(),
                    base.capabilities(),
                    null
            );
        }

        Set<SemanticRole> mergedRoles = new LinkedHashSet<>(base.roles());
        mergedRoles.addAll(binding.roles());

        Set<Capability> mergedCapabilities = new LinkedHashSet<>(base.capabilities());
        mergedCapabilities.addAll(binding.capabilities());

        return new EnrichedContent(
                base.key(),
                base.displayName(),
                mergedRoles,
                mergedCapabilities,
                binding
        );
    }

    public Optional<ContentBinding> optionalBinding() {
        return Optional.ofNullable(binding);
    }
}
