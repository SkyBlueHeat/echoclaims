package dev.worldecho.domain.binding;

import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingRegistryTest {

    @Test
    void entityLookupFindsBinding() {
        ContentKey key = new ContentKey("minecraft", "zombie");
        ContentBinding binding = new ContentBinding(
                key, BindingType.ENTITY,
                Set.of(SemanticRole.MONSTER), Set.of(Capability.CAN_FIGHT),
                null, null, null, Set.of("undead")
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(key, binding), Map.of(), List.of(), 1
        );

        Optional<ContentBinding> found = registry.findEntityBinding(key);
        assertTrue(found.isPresent());
        assertEquals(BindingType.ENTITY, found.get().type());
    }

    @Test
    void itemLookupFindsBinding() {
        ContentKey key = new ContentKey("minecraft", "diamond_sword");
        ContentBinding binding = new ContentBinding(
                key, BindingType.ITEM,
                Set.of(SemanticRole.WEAPON), Set.of(Capability.CAN_CHANGE_OWNER),
                null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(), Map.of(key, binding), List.of(), 1
        );

        Optional<ContentBinding> found = registry.findItemBinding(key);
        assertTrue(found.isPresent());
        assertEquals(BindingType.ITEM, found.get().type());
    }

    @Test
    void missingLookupReturnsEmpty() {
        BindingRegistry registry = BindingRegistry.empty();

        assertTrue(registry.findEntityBinding(new ContentKey("minecraft", "zombie")).isEmpty());
        assertTrue(registry.findItemBinding(new ContentKey("minecraft", "zombie")).isEmpty());
    }

    @Test
    void entityAndItemNamespacesAreSeparate() {
        ContentKey key = new ContentKey("minecraft", "skeleton");
        ContentBinding entityBinding = new ContentBinding(
                key, BindingType.ENTITY, Set.of(SemanticRole.MONSTER), Set.of(), null, null, null, Set.of()
        );
        ContentBinding itemBinding = new ContentBinding(
                key, BindingType.ITEM, Set.of(SemanticRole.RELIC), Set.of(), null, null, null, Set.of()
        );
        BindingRegistry registry = new BindingRegistry(
                Map.of(key, entityBinding), Map.of(key, itemBinding), List.of(), 1
        );

        assertEquals(BindingType.ENTITY, registry.findEntityBinding(key).get().type());
        assertEquals(BindingType.ITEM, registry.findItemBinding(key).get().type());
        assertTrue(registry.findEntityBinding(key).get().roles().contains(SemanticRole.MONSTER));
        assertTrue(registry.findItemBinding(key).get().roles().contains(SemanticRole.RELIC));
    }

    @Test
    void countsAreCorrect() {
        ContentKey eKey1 = new ContentKey("minecraft", "zombie");
        ContentKey eKey2 = new ContentKey("minecraft", "skeleton");
        ContentKey iKey1 = new ContentKey("minecraft", "diamond_sword");
        BindingRegistry registry = new BindingRegistry(
                Map.of(
                        eKey1, new ContentBinding(eKey1, BindingType.ENTITY, Set.of(), Set.of(), null, null, null, Set.of()),
                        eKey2, new ContentBinding(eKey2, BindingType.ENTITY, Set.of(), Set.of(), null, null, null, Set.of())
                ),
                Map.of(iKey1, new ContentBinding(iKey1, BindingType.ITEM, Set.of(), Set.of(), null, null, null, Set.of())),
                List.of(), 1
        );

        assertEquals(2, registry.entityBindingCount());
        assertEquals(1, registry.itemBindingCount());
    }

    @Test
    void diagnosticsAreCountedCorrectly() {
        List<BindingDiagnostic> diagnostics = List.of(
                new BindingDiagnostic(BindingDiagnostic.Severity.WARNING, "a", "w1"),
                new BindingDiagnostic(BindingDiagnostic.Severity.WARNING, "b", "w2"),
                new BindingDiagnostic(BindingDiagnostic.Severity.ERROR, "c", "e1")
        );
        BindingRegistry registry = new BindingRegistry(Map.of(), Map.of(), diagnostics, 1);

        assertEquals(2, registry.warningCount());
        assertEquals(1, registry.errorCount());
    }

    @Test
    void keysAreImmutable() {
        ContentKey key = new ContentKey("minecraft", "zombie");
        BindingRegistry registry = new BindingRegistry(
                Map.of(key, new ContentBinding(key, BindingType.ENTITY, Set.of(), Set.of(), null, null, null, Set.of())),
                Map.of(), List.of(), 1
        );

        try {
            registry.entityKeys().add(new ContentKey("x", "y"));
            assert false : "entityKeys should be immutable";
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    void emptyRegistryHasZeroCounts() {
        BindingRegistry registry = BindingRegistry.empty();

        assertEquals(0, registry.entityBindingCount());
        assertEquals(0, registry.itemBindingCount());
        assertEquals(0, registry.warningCount());
        assertEquals(0, registry.errorCount());
        assertEquals(0, registry.schemaVersion());
    }

    @Test
    void schemaVersionIsPreserved() {
        BindingRegistry registry = new BindingRegistry(Map.of(), Map.of(), List.of(), 1);
        assertEquals(1, registry.schemaVersion());
    }
}
