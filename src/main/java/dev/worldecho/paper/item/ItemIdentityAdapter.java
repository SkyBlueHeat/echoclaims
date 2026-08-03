package dev.worldecho.paper.item;

import dev.worldecho.domain.item.TrackedItemId;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Reads and writes WorldEcho tracked-item IDs on Bukkit {@link ItemStack}s using the
 * Persistent Data Container.
 *
 * <p>Read operations never mutate the item.  Assignment operations only mutate when
 * explicitly requested.
 */
public final class ItemIdentityAdapter {

    public enum IdentityStatus {
        EXISTING,
        ASSIGNED,
        MISSING,
        MALFORMED,
        UNSUPPORTED_ITEM
    }

    public record IdentityResult(IdentityStatus status, TrackedItemId itemId) {
        public Optional<TrackedItemId> optionalItemId() {
            return Optional.ofNullable(itemId);
        }
    }

    private final NamespacedKey key;

    public ItemIdentityAdapter(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "item_id");
    }

    public NamespacedKey key() {
        return key;
    }

    /**
     * Pure-Java decision logic that maps a stored PDC string value to an
     * {@link IdentityResult}.  Extracted for testability without Bukkit.
     */
    static IdentityResult resolveStoredValue(String stored) {
        if (stored == null) {
            return new IdentityResult(IdentityStatus.MISSING, null);
        }
        Optional<TrackedItemId> parsed = TrackedItemId.tryParse(stored);
        if (parsed.isEmpty()) {
            return new IdentityResult(IdentityStatus.MALFORMED, null);
        }
        return new IdentityResult(IdentityStatus.EXISTING, parsed.get());
    }

    public IdentityResult readIdentity(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new IdentityResult(IdentityStatus.UNSUPPORTED_ITEM, null);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new IdentityResult(IdentityStatus.UNSUPPORTED_ITEM, null);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String stored = pdc.get(key, PersistentDataType.STRING);
        return resolveStoredValue(stored);
    }

    public IdentityResult ensureIdentity(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new IdentityResult(IdentityStatus.UNSUPPORTED_ITEM, null);
        }
        IdentityResult existing = readIdentity(item);
        if (existing.status() == IdentityStatus.EXISTING) {
            return existing;
        }
        if (existing.status() == IdentityStatus.MALFORMED) {
            return existing;
        }
        if (existing.status() == IdentityStatus.UNSUPPORTED_ITEM) {
            return existing;
        }
        TrackedItemId newId = TrackedItemId.random();
        assignIdentity(item, newId);
        return new IdentityResult(IdentityStatus.ASSIGNED, newId);
    }

    public IdentityResult assignNewIdentity(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new IdentityResult(IdentityStatus.UNSUPPORTED_ITEM, null);
        }
        TrackedItemId newId = TrackedItemId.random();
        assignIdentity(item, newId);
        return new IdentityResult(IdentityStatus.ASSIGNED, newId);
    }

    /**
     * Writes an existing tracked-item ID onto an item's PDC.
     * Used for transformation identity continuity (anvil, smithing, grindstone).
     *
     * @return true if the identity was written, false if the item is unsupported
     */
    public boolean writeIdentity(ItemStack item, TrackedItemId id) {
        if (item == null || item.getType().isAir() || id == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        item.setItemMeta(meta);
        return true;
    }

    private void assignIdentity(ItemStack item, TrackedItemId id) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        item.setItemMeta(meta);
    }
}
