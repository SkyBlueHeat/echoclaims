package io.github.skyblueheat.echoclaims.integration;

import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Entry point the Paper layer uses to identify entities and items.
 *
 * <p>The registry owns two independent {@link ProviderRegistry} instances so an unhealthy
 * item bridge cannot affect entity identification.</p>
 */
public final class IntegrationRegistry {

    private final ProviderRegistry<Entity> entityProviders;
    private final ProviderRegistry<ItemStack> itemProviders;

    public IntegrationRegistry(BiConsumer<String, Throwable> failureHandler) {
        this.entityProviders = new ProviderRegistry<>(failureHandler);
        this.itemProviders = new ProviderRegistry<>(failureHandler);
    }

    public void registerEntityProvider(EntityContentProvider provider) {
        entityProviders.register(provider);
    }

    public void registerItemProvider(ItemContentProvider provider) {
        itemProviders.register(provider);
    }

    public Optional<IdentifiedContent> identifyEntity(Entity entity) {
        return entityProviders.identify(entity);
    }

    public Optional<IdentifiedContent> identifyItem(ItemStack itemStack) {
        return itemProviders.identify(itemStack);
    }

    public List<String> describeProviders() {
        List<String> result = new ArrayList<>();
        entityProviders.statuses()
                .forEach(status -> result.add("entity:" + status.describe()));
        itemProviders.statuses()
                .forEach(status -> result.add("item:" + status.describe()));
        return List.copyOf(result);
    }

    public List<ProviderStatus> entityProviderStatuses() {
        return entityProviders.statuses();
    }

    public List<ProviderStatus> itemProviderStatuses() {
        return itemProviders.statuses();
    }

    public void freeze() {
        entityProviders.freeze();
        itemProviders.freeze();
    }
}
