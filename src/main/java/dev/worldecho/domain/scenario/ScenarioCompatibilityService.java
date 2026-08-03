package dev.worldecho.domain.scenario;

import dev.worldecho.domain.content.IdentifiedContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ScenarioCompatibilityService {

    public CompatibilityResult evaluate(
            IdentifiedContent content,
            ContentRequirements requirements
    ) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(requirements, "requirements");

        List<String> reasons = new ArrayList<>();

        requirements.requiredRoles().stream()
                .filter(role -> !content.roles().contains(role))
                .map(role -> "Missing role: " + role)
                .forEach(reasons::add);

        requirements.requiredCapabilities().stream()
                .filter(capability -> !content.capabilities().contains(capability))
                .map(capability -> "Missing capability: " + capability)
                .forEach(reasons::add);

        return reasons.isEmpty()
                ? CompatibilityResult.success()
                : CompatibilityResult.incompatible(reasons);
    }
}
