package dev.worldecho.application;

import dev.worldecho.domain.binding.BindingRegistry;
import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.binding.EnrichedContent;
import dev.worldecho.domain.content.IdentifiedContent;

import java.util.Objects;
import java.util.Optional;

/**
 * Enriches provider-identified content with server-owner configured bindings.
 *
 * <p>The enricher is a pure function of {@link IdentifiedContent} and
 * {@link BindingRegistry}: it never mutates the base content, never calls Bukkit,
 * and never depends on provider installation status.</p>
 */
public final class BindingEnricher {

    private final BindingRegistry registry;

    public BindingEnricher(BindingRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public BindingRegistry registry() {
        return registry;
    }

    /**
     * Enriches an entity-identified content with the matching entity binding, if any.
     */
    public EnrichedContent enrichEntity(IdentifiedContent base) {
        Objects.requireNonNull(base, "base");
        Optional<dev.worldecho.domain.binding.ContentBinding> binding =
                registry.findEntityBinding(base.key());
        return EnrichedContent.of(base, binding.orElse(null));
    }

    /**
     * Enriches an item-identified content with the matching item binding, if any.
     */
    public EnrichedContent enrichItem(IdentifiedContent base) {
        Objects.requireNonNull(base, "base");
        Optional<dev.worldecho.domain.binding.ContentBinding> binding =
                registry.findItemBinding(base.key());
        return EnrichedContent.of(base, binding.orElse(null));
    }
}
