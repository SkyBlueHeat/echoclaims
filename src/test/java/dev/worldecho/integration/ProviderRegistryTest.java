package dev.worldecho.integration;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {

    private final List<String> failures = new ArrayList<>();

    @Test
    void highestPriorityProviderWins() {
        ProviderRegistry<String> registry = registry();
        registry.register(new FakeProvider("vanilla", Integer.MIN_VALUE));
        registry.register(new FakeProvider("mythicmobs", 100));
        registry.register(new FakeProvider("citizens", 50));

        assertEquals("mythicmobs", identify(registry, "boss"));
        assertEquals(List.of("mythicmobs", "citizens", "vanilla"), registry.providerIds());
    }

    @Test
    void fallsBackToVanillaWhenNoBridgeClaimsTheContent() {
        ProviderRegistry<String> registry = registry();
        registry.register(new FakeProvider("vanilla", Integer.MIN_VALUE));
        registry.register(new FakeProvider("mythicmobs", 100, ProviderHealth.AVAILABLE, false));

        assertEquals("vanilla", identify(registry, "zombie"));
    }

    @Test
    void unavailableProvidersAreSkipped() {
        ProviderRegistry<String> registry = registry();
        registry.register(new FakeProvider("vanilla", Integer.MIN_VALUE));
        registry.register(new FakeProvider(
                "oraxen", 100, ProviderHealth.UNAVAILABLE, true));

        assertEquals("vanilla", identify(registry, "sword"));
    }

    @Test
    void aThrowingBridgeIsSuppressedWithoutBreakingTheCore() {
        ProviderRegistry<String> registry = new ProviderRegistry<>(
                (providerId, throwable) -> failures.add(providerId), 2);
        registry.register(new FakeProvider("vanilla", Integer.MIN_VALUE));
        registry.register(new ExplodingProvider("broken", 100));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals("vanilla", identify(registry, "sword"));
        }

        assertTrue(registry.isSuppressed("broken"));
        assertEquals(2, failures.size(), "failures should be logged only until suppression");
        assertTrue(registry.statuses().stream()
                .anyMatch(status -> status.providerId().equals("broken") && status.suppressed()));
    }

    @Test
    void registeringTheSameProviderTwiceIsRejected() {
        ProviderRegistry<String> registry = registry();
        registry.register(new FakeProvider("vanilla", Integer.MIN_VALUE));

        assertThrows(IllegalStateException.class,
                () -> registry.register(new FakeProvider("vanilla", 10)));
    }

    @Test
    void emptyRegistryIdentifiesNothing() {
        assertFalse(registry().identify("zombie").isPresent());
    }

    private ProviderRegistry<String> registry() {
        return new ProviderRegistry<>((providerId, throwable) -> failures.add(providerId));
    }

    private static String identify(ProviderRegistry<String> registry, String source) {
        return registry.identify(source)
                .map(content -> content.key().providerId())
                .orElse("none");
    }

    private static class FakeProvider implements ContentProvider<String> {

        private final String providerId;
        private final int priority;
        private final ProviderHealth health;
        private final boolean claims;

        FakeProvider(String providerId, int priority) {
            this(providerId, priority, ProviderHealth.AVAILABLE, true);
        }

        FakeProvider(String providerId, int priority, ProviderHealth health, boolean claims) {
            this.providerId = providerId;
            this.priority = priority;
            this.health = health;
            this.claims = claims;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public ProviderHealth health() {
            return health;
        }

        @Override
        public boolean supports(String source) {
            return claims;
        }

        @Override
        public Optional<IdentifiedContent> identify(String source) {
            return Optional.of(new IdentifiedContent(
                    new ContentKey(providerId, source), source, Set.of(), Set.of()));
        }
    }

    private static final class ExplodingProvider extends FakeProvider {

        ExplodingProvider(String providerId, int priority) {
            super(providerId, priority);
        }

        @Override
        public boolean supports(String source) {
            throw new IllegalStateException("bridge is broken");
        }
    }
}
