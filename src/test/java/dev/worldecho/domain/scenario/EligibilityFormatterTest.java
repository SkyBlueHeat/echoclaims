package dev.worldecho.domain.scenario;

import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.binding.ContentBinding;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityFormatterTest {

    private final EligibilityEvaluator evaluator =
            new EligibilityEvaluator(EligibilityCatalog.builtin());

    private static final ContentKey ZOMBIE = new ContentKey("minecraft", "zombie");

    @Test
    void formatProfilesListsAllProfiles() {
        List<String> lines = EligibilityFormatter.formatProfiles(EligibilityCatalog.builtin());

        assertEquals(4, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("item-carrier")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("promotion-candidate")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("combat-story-actor")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("transferable-story-item")));
    }

    @Test
    void formatProfilesIncludesBindingTypeAndRequirements() {
        List<String> lines = EligibilityFormatter.formatProfiles(EligibilityCatalog.builtin());

        String itemCarrier = lines.stream()
                .filter(l -> l.startsWith("item-carrier"))
                .findFirst().orElseThrow();
        assertTrue(itemCarrier.contains("[entity]"));
        assertTrue(itemCarrier.contains("cap:CAN_HOLD_ITEMS"));
    }

    @Test
    void formatResultForEligibleContent() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE, BindingType.ENTITY,
                Set.of(SemanticRole.MONSTER), Set.of(Capability.CAN_FIGHT),
                null, null, null, Set.of("undead")
        );
        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        List<String> lines = EligibilityFormatter.formatResult(result);

        assertTrue(lines.stream().anyMatch(l -> l.contains("content: minecraft:zombie")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("profile: combat-story-actor")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("result: eligible")));
    }

    @Test
    void formatResultForNotEligibleContent() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE, BindingType.ENTITY,
                Set.of(SemanticRole.MONSTER), Set.of(),
                null, null, null, Set.of()
        );
        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        List<String> lines = EligibilityFormatter.formatResult(result);

        assertTrue(lines.stream().anyMatch(l -> l.contains("result: not eligible")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Missing capability: CAN_FIGHT")));
    }

    @Test
    void formatSummaryForEligible() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE, BindingType.ENTITY,
                Set.of(SemanticRole.MONSTER), Set.of(Capability.CAN_FIGHT),
                null, null, null, Set.of()
        );
        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        String summary = EligibilityFormatter.formatSummary(result);

        assertEquals("combat-story-actor: eligible", summary);
    }

    @Test
    void formatSummaryForNotEligible() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE, BindingType.ENTITY,
                Set.of(), Set.of(),
                null, null, null, Set.of()
        );
        EligibilityResult result = evaluator.evaluate(binding, "promotion-candidate");

        String summary = EligibilityFormatter.formatSummary(result);

        assertTrue(summary.startsWith("promotion-candidate: missing"));
        assertTrue(summary.contains("can-be-promoted"));
        assertTrue(summary.contains("faction"));
        assertTrue(summary.contains("rank"));
    }

    @Test
    void formatResultForUnknownProfile() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE, BindingType.ENTITY,
                Set.of(), Set.of(), null, null, null, Set.of()
        );
        EligibilityResult result = evaluator.evaluate(binding, "nonexistent");

        List<String> lines = EligibilityFormatter.formatResult(result);

        assertTrue(lines.stream().anyMatch(l -> l.contains("result: not eligible")));
    }

    @Test
    void formatResultForWrongBindingType() {
        ContentBinding binding = new ContentBinding(
                new ContentKey("minecraft", "diamond_sword"),
                BindingType.ITEM,
                Set.of(), Set.of(), null, null, null, Set.of()
        );
        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        List<String> lines = EligibilityFormatter.formatResult(result);

        assertTrue(lines.stream().anyMatch(l -> l.contains("result: not eligible")));
    }
}
