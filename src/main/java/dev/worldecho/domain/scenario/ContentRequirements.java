package dev.worldecho.domain.scenario;

import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.SemanticRole;

import java.util.Objects;
import java.util.Set;

public record ContentRequirements(
        Set<SemanticRole> requiredRoles,
        Set<Capability> requiredCapabilities
) {

    public ContentRequirements {
        requiredRoles = Set.copyOf(Objects.requireNonNullElse(requiredRoles, Set.of()));
        requiredCapabilities = Set.copyOf(
                Objects.requireNonNullElse(requiredCapabilities, Set.of())
        );
    }
}
