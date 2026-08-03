package dev.worldecho.domain.binding;

import dev.worldecho.domain.content.ContentKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable lookup of {@link ContentBinding}s by {@link ContentKey}, separated by
 * {@link BindingType} so an entity binding and an item binding may share the same key.
 */
public record BindingRegistry(
        Map<ContentKey, ContentBinding> entityBindings,
        Map<ContentKey, ContentBinding> itemBindings,
        List<BindingDiagnostic> diagnostics,
        int schemaVersion
) {

    private static final BindingRegistry EMPTY = new BindingRegistry(
            Map.of(), Map.of(), List.of(), 0
    );

    public BindingRegistry {
        entityBindings = Map.copyOf(Objects.requireNonNull(entityBindings, "entityBindings"));
        itemBindings = Map.copyOf(Objects.requireNonNull(itemBindings, "itemBindings"));
        diagnostics = List.copyOf(Objects.requireNonNullElse(diagnostics, List.of()));
    }

    public static BindingRegistry empty() {
        return EMPTY;
    }

    public Optional<ContentBinding> findEntityBinding(ContentKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(entityBindings.get(key));
    }

    public Optional<ContentBinding> findItemBinding(ContentKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(itemBindings.get(key));
    }

    public int entityBindingCount() {
        return entityBindings.size();
    }

    public int itemBindingCount() {
        return itemBindings.size();
    }

    public long warningCount() {
        return diagnostics.stream().filter(d -> !d.isError()).count();
    }

    public long errorCount() {
        return diagnostics.stream().filter(BindingDiagnostic::isError).count();
    }

    public Set<ContentKey> entityKeys() {
        return entityBindings.keySet();
    }

    public Set<ContentKey> itemKeys() {
        return itemBindings.keySet();
    }
}
