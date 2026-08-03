package dev.worldecho.integration;

import org.bukkit.inventory.ItemStack;

/**
 * Identifies an item stack, for example an Oraxen or ItemsAdder item.
 *
 * <p>Implementations must not mutate the inspected stack and are called on the server
 * thread only.</p>
 */
public interface ItemContentProvider extends ContentProvider<ItemStack> {
}
