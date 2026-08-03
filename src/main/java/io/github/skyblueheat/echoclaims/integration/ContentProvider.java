package io.github.skyblueheat.echoclaims.integration;

import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;

import java.util.Optional;

/**
 * Identifies objects of type {@code S} as provider-neutral EchoClaims content.
 *
 * <p>Bridges to external plugins implement this interface. The claim engine only ever
 * sees {@link IdentifiedContent}, so a new bridge never requires an engine change.</p>
 *
 * @param <S> the runtime object a provider can inspect, for example an entity or an item
 */
public interface ContentProvider<S> {

    String providerId();

    /**
     * Higher priority wins. Vanilla fallback providers use {@link Integer#MIN_VALUE}.
     */
    int priority();

    ProviderHealth health();

    boolean supports(S source);

    Optional<IdentifiedContent> identify(S source);
}
