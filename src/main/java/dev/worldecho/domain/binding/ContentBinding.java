package dev.worldecho.domain.binding;

import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable semantic metadata that a server owner has assigned to a specific content key.
 *
 * <p>A binding enriches — never replaces — the roles and capabilities a provider supplies.
 * Optional {@code faction}, {@code rank}, {@code superior}, and {@code tags} are metadata
 * only in Sprint 0.2A; no behaviour is attached to them yet.</p>
 */
public record ContentBinding(
        ContentKey key,
        BindingType type,
        Set<SemanticRole> roles,
        Set<Capability> capabilities,
        String faction,
        String rank,
        ContentKey superior,
        Set<String> tags
) {

    public ContentBinding {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        roles = Set.copyOf(Objects.requireNonNullElse(roles, Set.of()));
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
        faction = normalizeOptional(faction);
        rank = normalizeOptional(rank);
        tags = Set.copyOf(Objects.requireNonNullElse(tags, Set.of()));
    }

    public Optional<String> optionalFaction() {
        return Optional.ofNullable(faction);
    }

    public Optional<String> optionalRank() {
        return Optional.ofNullable(rank);
    }

    public Optional<ContentKey> optionalSuperior() {
        return Optional.ofNullable(superior);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
