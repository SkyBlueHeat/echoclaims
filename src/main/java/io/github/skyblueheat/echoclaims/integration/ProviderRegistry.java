package io.github.skyblueheat.echoclaims.integration;

import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Priority-ordered lookup across content providers with a vanilla fallback.
 *
 * <p>A provider that throws is suppressed after {@code failureLimit} errors so a broken
 * bridge disables itself instead of the EchoClaims core.</p>
 *
 * @param <S> the runtime object providers inspect
 */
public final class ProviderRegistry<S> {

    private static final int DEFAULT_FAILURE_LIMIT = 3;

    private final List<ContentProvider<S>> providers = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicLong> failures = new ConcurrentHashMap<>();
    private final BiConsumer<String, Throwable> failureHandler;
    private final int failureLimit;
    private final AtomicBoolean frozen = new AtomicBoolean(false);

    public ProviderRegistry(BiConsumer<String, Throwable> failureHandler) {
        this(failureHandler, DEFAULT_FAILURE_LIMIT);
    }

    public ProviderRegistry(BiConsumer<String, Throwable> failureHandler, int failureLimit) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
        this.failureLimit = Math.max(1, failureLimit);
    }

    public void register(ContentProvider<S> provider) {
        Objects.requireNonNull(provider, "provider");
        if (frozen.get()) {
            throw new IllegalStateException("ProviderRegistry is frozen; no further registrations allowed");
        }
        synchronized (this) {
            if (providers.stream().anyMatch(known -> known.providerId().equals(provider.providerId()))) {
                throw new IllegalStateException(
                        "Provider already registered: " + provider.providerId());
            }

            List<ContentProvider<S>> ordered = new ArrayList<>(providers);
            ordered.add(provider);
            ordered.sort(
                    Comparator.comparingInt(ContentProvider<S>::priority).reversed()
                            .thenComparing(ContentProvider::providerId)
            );

            providers.clear();
            providers.addAll(ordered);
            failures.putIfAbsent(provider.providerId(), new AtomicLong());
        }
    }

    public void freeze() {
        frozen.set(true);
    }

    public boolean isFrozen() {
        return frozen.get();
    }

    public Optional<IdentifiedContent> identify(S source) {
        if (source == null) {
            return Optional.empty();
        }

        for (ContentProvider<S> provider : providers) {
            if (!isUsable(provider)) {
                continue;
            }

            try {
                if (!provider.supports(source)) {
                    continue;
                }
                Optional<IdentifiedContent> identified = provider.identify(source);
                if (identified.isPresent()) {
                    return identified;
                }
            } catch (RuntimeException | LinkageError exception) {
                recordFailure(provider, exception);
            }
        }

        return Optional.empty();
    }

    public List<ProviderStatus> statuses() {
        return providers.stream()
                .map(provider -> new ProviderStatus(
                        provider.providerId(),
                        provider.priority(),
                        safeHealth(provider),
                        isSuppressed(provider.providerId()),
                        failureCount(provider.providerId())
                ))
                .toList();
    }

    public List<String> providerIds() {
        return providers.stream().map(ContentProvider::providerId).toList();
    }

    public long failureCount(String providerId) {
        AtomicLong counter = failures.get(providerId);
        return counter == null ? 0L : counter.get();
    }

    public boolean isSuppressed(String providerId) {
        return failureCount(providerId) >= failureLimit;
    }

    private boolean isUsable(ContentProvider<S> provider) {
        return !isSuppressed(provider.providerId())
                && safeHealth(provider) == ProviderHealth.AVAILABLE;
    }

    private ProviderHealth safeHealth(ContentProvider<S> provider) {
        try {
            return Objects.requireNonNullElse(provider.health(), ProviderHealth.UNAVAILABLE);
        } catch (RuntimeException | LinkageError exception) {
            recordFailure(provider, exception);
            return ProviderHealth.UNAVAILABLE;
        }
    }

    private void recordFailure(ContentProvider<S> provider, Throwable throwable) {
        long count = failures
                .computeIfAbsent(provider.providerId(), key -> new AtomicLong())
                .incrementAndGet();
        if (count <= failureLimit) {
            failureHandler.accept(provider.providerId(), throwable);
        }
    }
}
