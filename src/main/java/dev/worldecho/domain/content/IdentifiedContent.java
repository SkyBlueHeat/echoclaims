package dev.worldecho.domain.content;

import java.util.Objects;
import java.util.Set;

public record IdentifiedContent(
        ContentKey key,
        String displayName,
        Set<SemanticRole> roles,
        Set<Capability> capabilities
) {

    public IdentifiedContent {
        Objects.requireNonNull(key, "key");
        displayName = Objects.requireNonNullElse(displayName, key.toString());
        roles = Set.copyOf(Objects.requireNonNullElse(roles, Set.of()));
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
    }
}
