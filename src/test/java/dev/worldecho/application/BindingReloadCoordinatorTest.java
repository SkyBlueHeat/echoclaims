package dev.worldecho.application;

import dev.worldecho.config.BindingLoadResult;
import dev.worldecho.domain.binding.BindingDiagnostic;
import dev.worldecho.domain.binding.BindingRegistry;
import dev.worldecho.domain.binding.ContentBinding;
import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BindingReloadCoordinator}, which decides whether a newly loaded
 * registry should replace the currently active one.
 */
class BindingReloadCoordinatorTest {

    private static final ContentKey ZOMBIE = new ContentKey("minecraft", "zombie");
    private static final ContentKey SKELETON = new ContentKey("minecraft", "skeleton");

    private static BindingRegistry registryWith(ContentKey key) {
        ContentBinding binding = new ContentBinding(
                key, BindingType.ENTITY,
                Set.of(SemanticRole.MONSTER), Set.of(Capability.CAN_FIGHT),
                null, null, null, Set.of("undead")
        );
        return new BindingRegistry(
                Map.of(key, binding), Map.of(), List.of(), 1
        );
    }

    private static BindingLoadResult nonFatal(BindingRegistry registry) {
        return new BindingLoadResult(registry, List.of(), false);
    }

    private static BindingLoadResult fatal(BindingRegistry registry) {
        return new BindingLoadResult(registry, List.of(
                new BindingDiagnostic(BindingDiagnostic.Severity.ERROR, "bindings.yml",
                        "File could not be parsed")
        ), true);
    }

    @Test
    void startupWithNonFatalResultReplacesEmpty() {
        BindingRegistry newRegistry = registryWith(ZOMBIE);
        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(null, nonFatal(newRegistry));

        assertTrue(decision.replaced());
        assertFalse(decision.keptPrevious());
        assertSame(newRegistry, decision.registry());
    }

    @Test
    void startupWithFatalErrorFallsBackToEmptyRegistry() {
        BindingLoadResult fatalResult = fatal(BindingRegistry.empty());
        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(null, fatalResult);

        assertFalse(decision.keptPrevious());
        assertTrue(decision.replaced());
        assertEquals(0, decision.registry().entityBindingCount());
    }

    @Test
    void reloadWithNonFatalResultReplacesPrevious() {
        BindingRegistry previous = registryWith(ZOMBIE);
        BindingRegistry next = registryWith(SKELETON);

        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(previous, nonFatal(next));

        assertTrue(decision.replaced());
        assertFalse(decision.keptPrevious());
        assertSame(next, decision.registry());
        assertNotSame(previous, decision.registry());
    }

    @Test
    void reloadWithFatalErrorKeepsPreviousRegistry() {
        BindingRegistry previous = registryWith(ZOMBIE);
        BindingLoadResult fatalResult = fatal(BindingRegistry.empty());

        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(previous, fatalResult);

        assertTrue(decision.keptPrevious());
        assertFalse(decision.replaced());
        assertSame(previous, decision.registry());
    }

    @Test
    void reloadWithDiagnosticsButNoFatalReplacesPrevious() {
        BindingRegistry previous = registryWith(ZOMBIE);
        BindingRegistry next = registryWith(SKELETON);
        BindingLoadResult withWarnings = new BindingLoadResult(
                next,
                List.of(new BindingDiagnostic(
                        BindingDiagnostic.Severity.WARNING,
                        "bindings.entities.minecraft:creeper.roles[0]",
                        "Unknown role 'whatever'"
                )),
                false
        );

        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(previous, withWarnings);

        assertTrue(decision.replaced());
        assertFalse(decision.keptPrevious());
        assertSame(next, decision.registry());
    }

    @Test
    void repeatedReloadIsIdempotentWhenSameRegistryLoaded() {
        BindingRegistry registry = registryWith(ZOMBIE);
        BindingLoadResult result = nonFatal(registry);

        BindingReloadCoordinator.ReloadDecision first =
                BindingReloadCoordinator.decide(null, result);
        BindingReloadCoordinator.ReloadDecision second =
                BindingReloadCoordinator.decide(first.registry(), result);

        assertTrue(second.replaced());
        assertSame(registry, second.registry());
    }

    @Test
    void previousKeptOptionalIsEmptyWhenReplaced() {
        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(null, nonFatal(registryWith(ZOMBIE)));

        assertTrue(decision.previousKept().isEmpty());
    }

    @Test
    void previousKeptOptionalHasRegistryWhenKept() {
        BindingRegistry previous = registryWith(ZOMBIE);
        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(previous, fatal(BindingRegistry.empty()));

        assertTrue(decision.previousKept().isPresent());
        assertSame(previous, decision.previousKept().get());
    }
}
