package dev.worldecho.domain.scenario;

import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioCompatibilityServiceTest {

    private final ScenarioCompatibilityService service =
            new ScenarioCompatibilityService();

    @Test
    void acceptsContentWithAllRequiredRolesAndCapabilities() {
        IdentifiedContent soldier = new IdentifiedContent(
                new ContentKey("mythicmobs", "goblin_soldier"),
                "Goblin Soldier",
                Set.of(SemanticRole.SOLDIER),
                Set.of(
                        Capability.CAN_HOLD_ITEMS,
                        Capability.CAN_BE_PROMOTED
                )
        );

        ContentRequirements requirements = new ContentRequirements(
                Set.of(SemanticRole.SOLDIER),
                Set.of(
                        Capability.CAN_HOLD_ITEMS,
                        Capability.CAN_BE_PROMOTED
                )
        );

        assertTrue(service.evaluate(soldier, requirements).compatible());
    }

    @Test
    void rejectsContentAndExplainsMissingCapabilities() {
        IdentifiedContent animal = new IdentifiedContent(
                new ContentKey("vanilla", "minecraft:cow"),
                "Cow",
                Set.of(SemanticRole.ANIMAL),
                Set.of()
        );

        ContentRequirements requirements = new ContentRequirements(
                Set.of(SemanticRole.SOLDIER),
                Set.of(Capability.CAN_BE_PROMOTED)
        );

        CompatibilityResult result = service.evaluate(animal, requirements);

        assertFalse(result.compatible());
        assertTrue(result.reasons().stream().anyMatch(
                reason -> reason.contains("SOLDIER")
        ));
        assertTrue(result.reasons().stream().anyMatch(
                reason -> reason.contains("CAN_BE_PROMOTED")
        ));
    }
}
