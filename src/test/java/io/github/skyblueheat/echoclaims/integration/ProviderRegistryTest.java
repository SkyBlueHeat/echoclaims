package io.github.skyblueheat.echoclaims.integration;

import io.github.skyblueheat.echoclaims.domain.content.Capability;
import io.github.skyblueheat.echoclaims.domain.content.ContentKey;
import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;
import io.github.skyblueheat.echoclaims.domain.content.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {

    @Test
    void registersAndIdentifiesWithHighestPriorityProvider() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new StubProvider("vanilla", Integer.MIN_VALUE));
        registry.register(new StubProvider("oraxen", 100));

        Optional<IdentifiedContent> result = registry.identify("diamond_sword");

        assertTrue(result.isPresent());
        assertEquals("oraxen", result.get().key().providerId());
    }

    @Test
    void duplicateProviderIdIsRejected() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new StubProvider("vanilla", Integer.MIN_VALUE));

        assertThrows(IllegalStateException.class,
                () -> registry.register(new StubProvider("vanilla", 0)));
    }

    @Test
    void failingProviderIsSuppressedAfterFailureLimit() {
        CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> errors.add(t), 3);
        registry.register(new FailingProvider("broken", 100));
        registry.register(new StubProvider("vanilla", Integer.MIN_VALUE));

        for (int i = 0; i < 5; i++) {
            registry.identify("test");
        }

        assertTrue(registry.isSuppressed("broken"));
        assertEquals(3, errors.size());
    }

    @Test
    void nullSourceReturnsEmpty() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new StubProvider("vanilla", Integer.MIN_VALUE));

        assertTrue(registry.identify(null).isEmpty());
    }

    private static class StubProvider implements ContentProvider<String> {

        private final String id;
        private final int priority;

        StubProvider(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public ProviderHealth health() {
            return ProviderHealth.AVAILABLE;
        }

        @Override
        public boolean supports(String source) {
            return source != null;
        }

        @Override
        public Optional<IdentifiedContent> identify(String source) {
            return Optional.of(new IdentifiedContent(
                    new ContentKey(id, source),
                    source,
                    Set.of(),
                    Set.of(Capability.CAN_HAVE_HISTORY)
            ));
        }
    }

    private static class FailingProvider implements ContentProvider<String> {

        private final String id;
        private final AtomicInteger calls = new AtomicInteger();

        FailingProvider(String id, int priority) {
            this.id = id;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public ProviderHealth health() {
            return ProviderHealth.AVAILABLE;
        }

        @Override
        public boolean supports(String source) {
            return true;
        }

        @Override
        public Optional<IdentifiedContent> identify(String source) {
            throw new RuntimeException("boom #" + calls.incrementAndGet());
        }
    }
}
