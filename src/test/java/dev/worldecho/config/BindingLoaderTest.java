package dev.worldecho.config;

import dev.worldecho.domain.binding.BindingRegistry;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingLoaderTest {

    @Test
    void emptyConfigurationProducesEmptyRegistry() {
        BindingLoadResult result = BindingLoader.load(MapConfigurationSource.empty());

        assertEquals(0, result.registry().entityBindingCount());
        assertEquals(0, result.registry().itemBindingCount());
        assertTrue(result.fatalError());
    }

    @Test
    void missingVersionProducesFatalError() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("bindings", bindings("entities", Map.of(), "items", Map.of()));

        BindingLoadResult result = BindingLoader.load(new MapConfigurationSource(root));

        assertTrue(result.fatalError());
        assertEquals(0, result.registry().entityBindingCount());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.path().equals("version") && d.isError()));
    }

    @Test
    void unsupportedVersionProducesFatalError() {
        BindingLoadResult result = BindingLoader.load(source(99, bindings(
                "entities", Map.of(),
                "items", Map.of()
        )));

        assertTrue(result.fatalError());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.path().equals("version") && d.isError()));
    }

    @Test
    void nonNumericVersionProducesFatalError() {
        BindingLoadResult result = BindingLoader.load(new MapConfigurationSource(Map.of(
                "version", "abc"
        )));

        assertTrue(result.fatalError());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.path().equals("version") && d.isError()));
    }

    @Test
    void validEntityBindingLoads() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "roles", List.of("monster"),
                                "capabilities", List.of("can-fight", "can-hold-items"),
                                "tags", List.of("undead")
                        )
                ),
                "items", Map.of()
        )));

        assertFalse(result.fatalError());
        assertEquals(1, result.registry().entityBindingCount());
        assertEquals(0, result.registry().itemBindingCount());

        Optional<dev.worldecho.domain.binding.ContentBinding> binding =
                result.registry().findEntityBinding(new ContentKey("minecraft", "zombie"));
        assertTrue(binding.isPresent());
        assertTrue(binding.get().roles().contains(SemanticRole.MONSTER));
        assertTrue(binding.get().capabilities().contains(Capability.CAN_FIGHT));
        assertTrue(binding.get().tags().contains("undead"));
    }

    @Test
    void validItemBindingLoads() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(),
                "items", Map.of(
                        "minecraft:diamond_sword", binding(
                                "roles", List.of("weapon"),
                                "capabilities", List.of("can-change-owner")
                        )
                )
        )));

        assertEquals(0, result.registry().entityBindingCount());
        assertEquals(1, result.registry().itemBindingCount());

        Optional<dev.worldecho.domain.binding.ContentBinding> binding =
                result.registry().findItemBinding(new ContentKey("minecraft", "diamond_sword"));
        assertTrue(binding.isPresent());
        assertTrue(binding.get().roles().contains(SemanticRole.WEAPON));
    }

    @Test
    void entityAndItemBindingsMayShareSameContentKey() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:skeleton", binding("roles", List.of("monster"))
                ),
                "items", Map.of(
                        "minecraft:skeleton", binding("roles", List.of("relic"))
                )
        )));

        assertEquals(1, result.registry().entityBindingCount());
        assertEquals(1, result.registry().itemBindingCount());

        ContentKey key = new ContentKey("minecraft", "skeleton");
        assertTrue(result.registry().findEntityBinding(key).isPresent());
        assertTrue(result.registry().findItemBinding(key).isPresent());
        assertTrue(result.registry().findEntityBinding(key).get().roles().contains(SemanticRole.MONSTER));
        assertTrue(result.registry().findItemBinding(key).get().roles().contains(SemanticRole.RELIC));
    }

    @Test
    void providerMayBeUnavailableWithoutInvalidatingBinding() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "mythicmobs:goblin_soldier", binding(
                                "roles", List.of("soldier"),
                                "capabilities", List.of("can-fight", "can-be-promoted")
                        )
                ),
                "items", Map.of()
        )));

        assertFalse(result.fatalError());
        assertEquals(1, result.registry().entityBindingCount());
        assertTrue(result.registry().findEntityBinding(new ContentKey("mythicmobs", "goblin_soldier")).isPresent());
    }

    @Test
    void malformedContentKeyIsRejectedSafely() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "missing-colon", binding("roles", List.of("monster"))
                ),
                "items", Map.of()
        )));

        assertEquals(0, result.registry().entityBindingCount());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.isError() && d.path().contains("missing-colon")));
    }

    @Test
    void unknownRoleIsDiagnosedAndExcluded() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding("roles", List.of("monster", "astronaut"))
                ),
                "items", Map.of()
        )));

        assertEquals(1, result.registry().entityBindingCount());
        var binding = result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).get();
        assertTrue(binding.roles().contains(SemanticRole.MONSTER));
        assertFalse(binding.roles().stream().anyMatch(r -> r.name().equals("ASTRONAUT")));
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.path().contains("roles") && d.message().contains("astronaut")));
    }

    @Test
    void unknownCapabilityIsDiagnosedAndExcluded() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "capabilities", List.of("can-fight", "can-fly-to-the-moon")
                        )
                ),
                "items", Map.of()
        )));

        var binding = result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).get();
        assertTrue(binding.capabilities().contains(Capability.CAN_FIGHT));
        assertFalse(binding.capabilities().stream().anyMatch(c -> c.name().equals("CAN_FLY_TO_THE_MOON")));
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.path().contains("capabilities") && d.message().contains("can-fly-to-the-moon")));
    }

    @Test
    void validValuesSurviveNextToInvalidValues() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "roles", List.of("monster", "invalid-role"),
                                "capabilities", List.of("can-fight", "invalid-cap", "can-hold-items")
                        )
                ),
                "items", Map.of()
        )));

        var binding = result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).get();
        assertEquals(1, binding.roles().size());
        assertTrue(binding.roles().contains(SemanticRole.MONSTER));
        assertEquals(2, binding.capabilities().size());
        assertTrue(binding.capabilities().contains(Capability.CAN_FIGHT));
        assertTrue(binding.capabilities().contains(Capability.CAN_HOLD_ITEMS));
    }

    @Test
    void unknownYamlFieldProducesWarning() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "roles", List.of("monster"),
                                "capabilites", List.of("can-fight")
                        )
                ),
                "items", Map.of()
        )));

        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> !d.isError() && d.path().contains("capabilites")));
    }

    @Test
    void wrongTypeForRolesProducesDiagnostic() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding("roles", "monster")
                ),
                "items", Map.of()
        )));

        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.isError() && d.path().contains("roles")));
    }

    @Test
    void wrongTypeForCapabilitiesProducesDiagnostic() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding("capabilities", 42)
                ),
                "items", Map.of()
        )));

        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.isError() && d.path().contains("capabilities")));
    }

    @Test
    void wrongTypeForTagsProducesDiagnostic() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding("tags", "undead")
                ),
                "items", Map.of()
        )));

        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.isError() && d.path().contains("tags")));
    }

    @Test
    void blankFactionAndRankNormalizeToAbsent() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "roles", List.of("monster"),
                                "faction", "  ",
                                "rank", ""
                        )
                ),
                "items", Map.of()
        )));

        var binding = result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).get();
        assertTrue(binding.optionalFaction().isEmpty());
        assertTrue(binding.optionalRank().isEmpty());
    }

    @Test
    void duplicateAndBlankTagsAreRemoved() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "tags", List.of("undead", "undead", "  ", "monster")
                        )
                ),
                "items", Map.of()
        )));

        var binding = result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).get();
        assertEquals(2, binding.tags().size());
        assertTrue(binding.tags().contains("undead"));
        assertTrue(binding.tags().contains("monster"));
    }

    @Test
    void invalidSuperiorContentKeyIsDiagnosed() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "roles", List.of("monster"),
                                "superior", "missing-colon"
                        )
                ),
                "items", Map.of()
        )));

        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.isError() && d.path().contains("superior")));
        var binding = result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).get();
        assertTrue(binding.optionalSuperior().isEmpty());
    }

    @Test
    void unresolvedButValidSuperiorKeyIsAccepted() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding(
                                "roles", List.of("monster"),
                                "superior", "mythicmobs:goblin_captain"
                        )
                ),
                "items", Map.of()
        )));

        var binding = result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).get();
        assertTrue(binding.optionalSuperior().isPresent());
        assertEquals(new ContentKey("mythicmobs", "goblin_captain"), binding.optionalSuperior().get());
    }

    @Test
    void malformedEntryDoesNotDiscardValidEntries() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "bad-key", binding("roles", List.of("monster")),
                        "minecraft:zombie", binding("roles", List.of("monster"))
                ),
                "items", Map.of()
        )));

        assertEquals(1, result.registry().entityBindingCount());
        assertTrue(result.registry().findEntityBinding(new ContentKey("minecraft", "zombie")).isPresent());
    }

    @Test
    void loaderResultCollectionsAreImmutable() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "minecraft:zombie", binding("roles", List.of("monster"))
                ),
                "items", Map.of()
        )));

        BindingRegistry registry = result.registry();
        assertTrue(registry.entityKeys() instanceof java.util.Set);
        try {
            registry.entityKeys().add(new ContentKey("x", "y"));
            assert false : "entityKeys should be immutable";
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    void noBindingsSectionProducesWarning() {
        BindingLoadResult result = BindingLoader.load(source(1, null));

        assertFalse(result.fatalError());
        assertEquals(0, result.registry().entityBindingCount());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.path().equals("bindings")));
    }

    @Test
    void factionAndRankAreAcceptedAsMetadata() {
        BindingLoadResult result = BindingLoader.load(source(1, bindings(
                "entities", Map.of(
                        "mythicmobs:goblin_soldier", binding(
                                "roles", List.of("soldier"),
                                "faction", "goblin_clans",
                                "rank", "soldier",
                                "superior", "mythicmobs:goblin_captain",
                                "tags", List.of("goblin", "humanoid")
                        )
                ),
                "items", Map.of()
        )));

        var binding = result.registry().findEntityBinding(new ContentKey("mythicmobs", "goblin_soldier")).get();
        assertEquals("goblin_clans", binding.optionalFaction().orElse(null));
        assertEquals("soldier", binding.optionalRank().orElse(null));
        assertTrue(binding.optionalSuperior().isPresent());
        assertEquals(2, binding.tags().size());
    }

    // --- helpers ---

    private static Map<String, Object> binding(String k1, Object v1) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        return map;
    }

    private static MapConfigurationSource source(int version, Map<String, Object> bindings) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", version);
        if (bindings != null) {
            root.put("bindings", bindings);
        }
        return new MapConfigurationSource(root);
    }

    private static Map<String, Object> bindings(String entityKey, Object entityValue,
                                                  String itemKey, Object itemValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(entityKey, entityValue);
        map.put(itemKey, itemValue);
        return map;
    }

    private static Map<String, Object> binding(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static Map<String, Object> binding(String k1, Object v1, String k2, Object v2,
                                                  String k3, Object v3) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    private static Map<String, Object> binding(String k1, Object v1, String k2, Object v2,
                                                  String k3, Object v3, String k4, Object v4) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    private static Map<String, Object> binding(String k1, Object v1, String k2, Object v2,
                                                  String k3, Object v3, String k4, Object v4,
                                                  String k5, Object v5) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }
}
