package io.github.skyblueheat.echoclaims.integration;

import io.github.skyblueheat.echoclaims.domain.content.Capability;
import io.github.skyblueheat.echoclaims.domain.content.ContentKey;
import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryHardeningTest {

    @Test
    void equalPriorityProvidersAreOrderedByProviderId() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new StubProvider("zebra", 100));
        registry.register(new StubProvider("alpha", 100));
        registry.register(new StubProvider("mango", 100));

        Optional<IdentifiedContent> result = registry.identify("test");

        assertTrue(result.isPresent());
        assertEquals("alpha", result.get().key().providerId(),
                "equal priority should fall back to alphabetical provider ID");
    }

    @Test
    void frozenRegistryRejectsRegistration() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new StubProvider("vanilla", 0));
        registry.freeze();

        assertThrows(IllegalStateException.class,
                () -> registry.register(new StubProvider("oraxen", 100)));
    }

    @Test
    void isFrozenReturnsTrueAfterFreeze() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        assertFalse(registry.isFrozen());
        registry.freeze();
        assertTrue(registry.isFrozen());
    }

    @Test
    void concurrentLookupIsSafe() throws InterruptedException {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new StubProvider("vanilla", 0));
        registry.freeze();

        int threads = 8;
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        Optional<IdentifiedContent> result = registry.identify("test");
                        assertTrue(result.isPresent());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void repeatedProviderExceptionsAreBoundedByFailureLimit() {
        AtomicInteger errorCount = new AtomicInteger();
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> errorCount.incrementAndGet(), 3);
        registry.register(new FailingProvider("broken", 100));
        registry.register(new StubProvider("vanilla", 0));

        for (int i = 0; i < 10; i++) {
            registry.identify("test");
        }

        assertEquals(3, errorCount.get(), "failure handler should only be called up to failureLimit");
        assertTrue(registry.isSuppressed("broken"));
    }

    @Test
    void providerReturningNullContentIsTreatedAsEmpty() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new NullReturningProvider("nullish", 100));
        registry.register(new StubProvider("vanilla", 0));

        Optional<IdentifiedContent> result = registry.identify("test");

        assertTrue(result.isPresent());
        assertEquals("vanilla", result.get().key().providerId());
    }

    @Test
    void providerReturningNullFromHealthIsTreatedAsUnavailable() {
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> {});
        registry.register(new NullHealthProvider("unhealthy", 100));
        registry.register(new StubProvider("vanilla", 0));

        Optional<IdentifiedContent> result = registry.identify("test");

        assertTrue(result.isPresent());
        assertEquals("vanilla", result.get().key().providerId());
    }

    @Test
    void healthThrowingExceptionIsCaughtAndRecorded() {
        AtomicInteger errorCount = new AtomicInteger();
        ProviderRegistry<String> registry = new ProviderRegistry<>((id, t) -> errorCount.incrementAndGet(), 3);
        registry.register(new HealthThrowingProvider("sick", 100));
        registry.register(new StubProvider("vanilla", 0));

        registry.identify("test");
        registry.identify("test");
        registry.identify("test");

        assertTrue(registry.isSuppressed("sick"));
    }

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }

    private static class StubProvider implements ContentProvider<String> {
        private final String id;
        private final int priority;

        StubProvider(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        @Override
        public String providerId() { return id; }

        @Override
        public int priority() { return priority; }

        @Override
        public ProviderHealth health() { return ProviderHealth.AVAILABLE; }

        @Override
        public boolean supports(String source) { return source != null; }

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

        FailingProvider(String id, int priority) { this.id = id; }

        @Override
        public String providerId() { return id; }

        @Override
        public int priority() { return 100; }

        @Override
        public ProviderHealth health() { return ProviderHealth.AVAILABLE; }

        @Override
        public boolean supports(String source) { return true; }

        @Override
        public Optional<IdentifiedContent> identify(String source) {
            throw new RuntimeException("boom");
        }
    }

    private static class NullReturningProvider implements ContentProvider<String> {
        private final String id;

        NullReturningProvider(String id, int priority) { this.id = id; }

        @Override
        public String providerId() { return id; }

        @Override
        public int priority() { return 100; }

        @Override
        public ProviderHealth health() { return ProviderHealth.AVAILABLE; }

        @Override
        public boolean supports(String source) { return true; }

        @Override
        public Optional<IdentifiedContent> identify(String source) {
            return null;
        }
    }

    private static class NullHealthProvider implements ContentProvider<String> {
        private final String id;

        NullHealthProvider(String id, int priority) { this.id = id; }

        @Override
        public String providerId() { return id; }

        @Override
        public int priority() { return 100; }

        @Override
        public ProviderHealth health() { return null; }

        @Override
        public boolean supports(String source) { return true; }

        @Override
        public Optional<IdentifiedContent> identify(String source) {
            return Optional.of(new IdentifiedContent(
                    new ContentKey(id, source), source, Set.of(), Set.of()));
        }
    }

    private static class HealthThrowingProvider implements ContentProvider<String> {
        private final String id;

        HealthThrowingProvider(String id, int priority) { this.id = id; }

        @Override
        public String providerId() { return id; }

        @Override
        public int priority() { return 100; }

        @Override
        public ProviderHealth health() { throw new RuntimeException("health check failed"); }

        @Override
        public boolean supports(String source) { return true; }

        @Override
        public Optional<IdentifiedContent> identify(String source) {
            return Optional.of(new IdentifiedContent(
                    new ContentKey(id, source), source, Set.of(), Set.of()));
        }
    }
}
