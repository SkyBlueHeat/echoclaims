package dev.worldecho.domain.scenario;

import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.binding.ContentBinding;
import dev.worldecho.domain.binding.EnrichedContent;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityEvaluatorTest {

    private final EligibilityEvaluator evaluator =
            new EligibilityEvaluator(EligibilityCatalog.builtin());

    private static final ContentKey ZOMBIE = new ContentKey("minecraft", "zombie");
    private static final ContentKey SWORD = new ContentKey("minecraft", "diamond_sword");

    private ContentBinding entityBinding(ContentKey key, Set<SemanticRole> roles,
                                          Set<Capability> caps, String faction,
                                          String rank, ContentKey superior,
                                          Set<String> tags) {
        return new ContentBinding(key, BindingType.ENTITY, roles, caps,
                faction, rank, superior, tags);
    }

    private ContentBinding itemBinding(ContentKey key, Set<SemanticRole> roles,
                                        Set<Capability> caps, Set<String> tags) {
        return new ContentBinding(key, BindingType.ITEM, roles, caps,
                null, null, null, tags);
    }

    // --- Eligible cases ---

    @Test
    void fullyMatchingEntityIsEligible() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_FIGHT),
                null, null, null, Set.of("undead"));

        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        assertTrue(result.eligible());
        assertEquals(EligibilityStatus.ELIGIBLE, result.status());
    }

    @Test
    void fullyMatchingItemIsEligible() {
        ContentBinding binding = itemBinding(SWORD,
                Set.of(SemanticRole.WEAPON),
                Set.of(Capability.CAN_CHANGE_OWNER, Capability.CAN_HAVE_HISTORY),
                Set.of("weapon"));

        EligibilityResult result = evaluator.evaluate(binding, "transferable-story-item");

        assertTrue(result.eligible());
    }

    // --- Missing requirements ---

    @Test
    void missingRoleIsDiagnosed() {
        EligibilityCatalog catalog = EligibilityCatalog.of(
                EligibilityProfile.entity("role-test")
                        .roles(SemanticRole.SOLDIER)
                        .build()
        );
        EligibilityEvaluator eval = new EligibilityEvaluator(catalog);

        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(), Set.of(),
                null, null, null, Set.of());

        EligibilityResult result = eval.evaluate(binding, "role-test");

        assertFalse(result.eligible());
        assertTrue(result.missingRoles().contains(SemanticRole.SOLDIER));
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.MISSING_ROLE));
    }

    @Test
    void missingCapabilityIsDiagnosed() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.MONSTER), Set.of(),
                null, null, null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        assertFalse(result.eligible());
        assertTrue(result.missingCapabilities().contains(Capability.CAN_FIGHT));
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.MISSING_CAPABILITY));
    }

    @Test
    void multipleMissingCapabilitiesAreAllReported() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(), Set.of(),
                null, null, null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "item-carrier");

        assertFalse(result.eligible());
        assertTrue(result.missingCapabilities().contains(Capability.CAN_HOLD_ITEMS));
    }

    @Test
    void missingFactionIsDiagnosed() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.SOLDIER), Set.of(Capability.CAN_BE_PROMOTED),
                null, "soldier", null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "promotion-candidate");

        assertFalse(result.eligible());
        assertTrue(result.missingMetadata().contains("faction"));
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.MISSING_FACTION));
    }

    @Test
    void missingRankIsDiagnosed() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.SOLDIER), Set.of(Capability.CAN_BE_PROMOTED),
                "goblin_clans", null, null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "promotion-candidate");

        assertFalse(result.eligible());
        assertTrue(result.missingMetadata().contains("rank"));
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.MISSING_RANK));
    }

    @Test
    void missingSuperiorIsDiagnosedWhenRequired() {
        EligibilityCatalog catalog = EligibilityCatalog.of(
                EligibilityProfile.entity("test-superior")
                        .requiresSuperior()
                        .build()
        );
        EligibilityEvaluator eval = new EligibilityEvaluator(catalog);

        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(), Set.of(), null, null, null, Set.of());

        EligibilityResult result = eval.evaluate(binding, "test-superior");

        assertFalse(result.eligible());
        assertTrue(result.missingMetadata().contains("superior"));
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.MISSING_SUPERIOR));
    }

    // --- Type errors ---

    @Test
    void wrongBindingTypeIsDiagnosed() {
        ContentBinding binding = itemBinding(SWORD, Set.of(), Set.of(), Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        assertFalse(result.eligible());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.WRONG_BINDING_TYPE));
    }

    @Test
    void entityBindingCannotSatisfyItemProfile() {
        ContentBinding binding = entityBinding(ZOMBIE, Set.of(), Set.of(), null, null, null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "transferable-story-item");

        assertFalse(result.eligible());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.WRONG_BINDING_TYPE));
    }

    @Test
    void itemBindingCannotSatisfyEntityProfile() {
        ContentBinding binding = itemBinding(SWORD, Set.of(), Set.of(), Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "item-carrier");

        assertFalse(result.eligible());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.WRONG_BINDING_TYPE));
    }

    // --- Unknown profile ---

    @Test
    void unknownProfileIsDiagnosed() {
        ContentBinding binding = entityBinding(ZOMBIE, Set.of(), Set.of(), null, null, null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "nonexistent-profile");

        assertFalse(result.eligible());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == EligibilityDiagnosticCode.PROFILE_NOT_FOUND));
    }

    // --- Extra values don't cause failure ---

    @Test
    void extraRolesAndCapabilitiesDoNotCauseFailure() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.MONSTER, SemanticRole.SOLDIER),
                Set.of(Capability.CAN_FIGHT, Capability.CAN_HOLD_ITEMS),
                null, null, null, Set.of("extra-tag"));

        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        assertTrue(result.eligible());
    }

    // --- Blank metadata ---

    @Test
    void blankMetadataCountsAsMissing() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.SOLDIER), Set.of(Capability.CAN_BE_PROMOTED),
                "  ", "  ", null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "promotion-candidate");

        assertFalse(result.eligible());
        assertTrue(result.missingMetadata().contains("faction"));
        assertTrue(result.missingMetadata().contains("rank"));
    }

    // --- Immutability ---

    @Test
    void resultsAndCollectionsAreImmutable() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.MONSTER), Set.of(Capability.CAN_FIGHT),
                null, null, null, Set.of());

        EligibilityResult result = evaluator.evaluate(binding, "combat-story-actor");

        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().add(null));
        assertThrows(UnsupportedOperationException.class, () -> result.matchedRoles().add(null));
        assertThrows(UnsupportedOperationException.class, () -> result.missingMetadata().add("x"));
    }

    // --- Deterministic ordering ---

    @Test
    void diagnosticOrderIsDeterministic() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(), Set.of(),
                null, null, null, Set.of());

        EligibilityResult r1 = evaluator.evaluate(binding, "promotion-candidate");
        EligibilityResult r2 = evaluator.evaluate(binding, "promotion-candidate");

        assertEquals(r1.diagnostics(), r2.diagnostics());
    }

    // --- Base binding not mutated ---

    @Test
    void baseBindingIsNotMutated() {
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.MONSTER), Set.of(Capability.CAN_FIGHT),
                null, null, null, Set.of("undead"));

        evaluator.evaluate(binding, "combat-story-actor");

        assertEquals(1, binding.roles().size());
        assertTrue(binding.roles().contains(SemanticRole.MONSTER));
        assertEquals(1, binding.capabilities().size());
    }

    // --- Enriched content ---

    @Test
    void enrichedContentIncludesProviderSuppliedRoles() {
        EligibilityCatalog catalog = EligibilityCatalog.of(
                EligibilityProfile.entity("role-from-provider")
                        .roles(SemanticRole.MONSTER)
                        .build()
        );
        EligibilityEvaluator eval = new EligibilityEvaluator(catalog);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of()
        );
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(), Set.of(),
                null, null, null, Set.of());
        EnrichedContent enriched = EnrichedContent.of(base, binding);

        EligibilityResult result = eval.evaluate(enriched, "role-from-provider");

        assertTrue(result.eligible());
        assertTrue(result.matchedRoles().contains(SemanticRole.MONSTER));
    }

    @Test
    void enrichedContentIncludesProviderSuppliedCapabilities() {
        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE, "Zombie",
                Set.of(),
                Set.of(Capability.CAN_FIGHT)
        );
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(), Set.of(),
                null, null, null, Set.of());
        EnrichedContent enriched = EnrichedContent.of(base, binding);

        EligibilityResult result = evaluator.evaluate(enriched, "combat-story-actor");

        assertTrue(result.eligible());
        assertTrue(result.matchedCapabilities().contains(Capability.CAN_FIGHT));
    }

    @Test
    void configuredMetadataSurvivesEvaluation() {
        ContentKey superior = new ContentKey("minecraft", "skeleton");
        ContentBinding binding = entityBinding(ZOMBIE,
                Set.of(SemanticRole.SOLDIER), Set.of(Capability.CAN_BE_PROMOTED),
                "goblin_clans", "soldier", superior, Set.of("goblin"));

        EligibilityResult result = evaluator.evaluate(binding, "promotion-candidate");

        assertTrue(result.eligible());
    }

    // --- Locale regression ---

    @Test
    void turkishLocaleDoesNotBreakProfileLookup() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            EligibilityEvaluator turkishEval =
                    new EligibilityEvaluator(EligibilityCatalog.builtin());

            ContentBinding binding = entityBinding(ZOMBIE,
                    Set.of(SemanticRole.MONSTER), Set.of(Capability.CAN_FIGHT),
                    null, null, null, Set.of());

            EligibilityResult result = turkishEval.evaluate(binding, "combat-story-actor");

            assertTrue(result.eligible());
            assertEquals("combat-story-actor", result.profileId());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void turkishLocaleDoesNotBreakCaseInsensitiveProfileId() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            EligibilityCatalog catalog = EligibilityCatalog.builtin();

            assertTrue(catalog.find("ITEM-CARRIER").isPresent());
            assertTrue(catalog.find("combat-story-actor").isPresent());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
