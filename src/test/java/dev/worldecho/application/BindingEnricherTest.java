package dev.worldecho.application;

import dev.worldecho.domain.binding.BindingRegistry;
import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.binding.ContentBinding;
import dev.worldecho.domain.binding.EnrichedContent;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingEnricherTest {

    private static final ContentKey ZOMBIE_KEY = new ContentKey("minecraft", "zombie");
    private static final ContentKey SWORD_KEY = new ContentKey("minecraft", "diamond_sword");

    @Test
    void configuredRolesMergeWithProviderRoles() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE_KEY, BindingType.ENTITY,
                Set.of(SemanticRole.SOLDIER),
                Set.of(Capability.CAN_BE_PROMOTED),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(ZOMBIE_KEY, binding), Map.of(), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_FIGHT)
        );

        EnrichedContent enriched = enricher.enrichEntity(base);

        assertTrue(enriched.roles().contains(SemanticRole.MONSTER));
        assertTrue(enriched.roles().contains(SemanticRole.SOLDIER));
        assertEquals(2, enriched.roles().size());
    }

    @Test
    void configuredCapabilitiesMergeWithProviderCapabilities() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE_KEY, BindingType.ENTITY,
                Set.of(),
                Set.of(Capability.CAN_BE_PROMOTED, Capability.CAN_FIGHT),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(ZOMBIE_KEY, binding), Map.of(), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(),
                Set.of(Capability.CAN_FIGHT, Capability.CAN_HOLD_ITEMS)
        );

        EnrichedContent enriched = enricher.enrichEntity(base);

        assertTrue(enriched.capabilities().contains(Capability.CAN_FIGHT));
        assertTrue(enriched.capabilities().contains(Capability.CAN_HOLD_ITEMS));
        assertTrue(enriched.capabilities().contains(Capability.CAN_BE_PROMOTED));
        assertEquals(3, enriched.capabilities().size());
    }

    @Test
    void duplicatesAreRemoved() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE_KEY, BindingType.ENTITY,
                Set.of(SemanticRole.MONSTER, SemanticRole.SOLDIER),
                Set.of(Capability.CAN_FIGHT, Capability.CAN_HOLD_ITEMS),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(ZOMBIE_KEY, binding), Map.of(), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_FIGHT)
        );

        EnrichedContent enriched = enricher.enrichEntity(base);

        assertEquals(2, enriched.roles().size());
        assertEquals(2, enriched.capabilities().size());
    }

    @Test
    void baseContentIsNotMutated() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE_KEY, BindingType.ENTITY,
                Set.of(SemanticRole.SOLDIER),
                Set.of(Capability.CAN_BE_PROMOTED),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(ZOMBIE_KEY, binding), Map.of(), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_FIGHT)
        );

        enricher.enrichEntity(base);

        assertEquals(1, base.roles().size());
        assertTrue(base.roles().contains(SemanticRole.MONSTER));
        assertEquals(1, base.capabilities().size());
        assertTrue(base.capabilities().contains(Capability.CAN_FIGHT));
    }

    @Test
    void missingBindingReturnsEquivalentContent() {
        BindingRegistry registry = BindingRegistry.empty();
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_FIGHT)
        );

        EnrichedContent enriched = enricher.enrichEntity(base);

        assertEquals(base.key(), enriched.key());
        assertEquals(base.displayName(), enriched.displayName());
        assertEquals(base.roles(), enriched.roles());
        assertEquals(base.capabilities(), enriched.capabilities());
        assertTrue(enriched.optionalBinding().isEmpty());
    }

    @Test
    void entityBindingDoesNotEnrichItemResults() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE_KEY, BindingType.ENTITY,
                Set.of(SemanticRole.SOLDIER),
                Set.of(Capability.CAN_BE_PROMOTED),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(ZOMBIE_KEY, binding), Map.of(), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_FIGHT)
        );

        EnrichedContent enriched = enricher.enrichItem(base);

        assertFalse(enriched.roles().contains(SemanticRole.SOLDIER));
        assertTrue(enriched.optionalBinding().isEmpty());
    }

    @Test
    void itemBindingDoesNotEnrichEntityResults() {
        ContentBinding binding = new ContentBinding(
                SWORD_KEY, BindingType.ITEM,
                Set.of(SemanticRole.RELIC),
                Set.of(Capability.CAN_BECOME_HEIRLOOM),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(), Map.of(SWORD_KEY, binding), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                SWORD_KEY, "Diamond Sword",
                Set.of(SemanticRole.WEAPON),
                Set.of(Capability.CAN_CHANGE_OWNER)
        );

        EnrichedContent enriched = enricher.enrichEntity(base);

        assertFalse(enriched.roles().contains(SemanticRole.RELIC));
        assertTrue(enriched.optionalBinding().isEmpty());
    }

    @Test
    void optionalMetadataSurvivesEnrichment() {
        ContentKey superior = new ContentKey("mythicmobs", "goblin_captain");
        ContentBinding binding = new ContentBinding(
                ZOMBIE_KEY, BindingType.ENTITY,
                Set.of(SemanticRole.SOLDIER),
                Set.of(Capability.CAN_FIGHT),
                "goblin_clans", "soldier", superior, Set.of("goblin", "humanoid")
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(ZOMBIE_KEY, binding), Map.of(), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_HOLD_ITEMS)
        );

        EnrichedContent enriched = enricher.enrichEntity(base);

        assertTrue(enriched.optionalBinding().isPresent());
        assertEquals("goblin_clans", enriched.optionalBinding().get().optionalFaction().orElse(null));
        assertEquals("soldier", enriched.optionalBinding().get().optionalRank().orElse(null));
        assertEquals(superior, enriched.optionalBinding().get().optionalSuperior().orElse(null));
        assertTrue(enriched.optionalBinding().get().tags().contains("goblin"));
        assertTrue(enriched.optionalBinding().get().tags().contains("humanoid"));
    }

    @Test
    void providerIdentityIsPreserved() {
        ContentBinding binding = new ContentBinding(
                ZOMBIE_KEY, BindingType.ENTITY,
                Set.of(SemanticRole.SOLDIER),
                Set.of(),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(ZOMBIE_KEY, binding), Map.of(), java.util.List.of(), 1
        );
        BindingEnricher enricher = new BindingEnricher(registry);

        IdentifiedContent base = new IdentifiedContent(
                ZOMBIE_KEY, "Zombie",
                Set.of(SemanticRole.MONSTER),
                Set.of(Capability.CAN_FIGHT)
        );

        EnrichedContent enriched = enricher.enrichEntity(base);

        assertEquals(ZOMBIE_KEY, enriched.key());
        assertEquals("Zombie", enriched.displayName());
    }
}
