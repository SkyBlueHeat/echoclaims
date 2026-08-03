package io.github.skyblueheat.echoclaims.integration.bukkit;

import io.github.skyblueheat.echoclaims.domain.item.ItemDescriptor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts live Bukkit item stacks into immutable {@link ItemDescriptor} records.
 *
 * <p>Every method must be called on the server thread. The returned descriptor is the
 * only representation that may cross a thread boundary.</p>
 */
public final class BukkitItems {

    private BukkitItems() {
    }

    /**
     * Reads an item without mutating it.
     *
     * @param identifyingProviderId provider that claimed the item, or an empty string
     */
    public static ItemDescriptor describe(ItemStack itemStack, String identifyingProviderId) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return ItemDescriptor.of("minecraft:air", 1);
        }

        Map<String, Integer> enchantments = new LinkedHashMap<>();
        String customName = "";
        boolean unbreakable = false;
        int damage = 0;

        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                enchantments.put(entry.getKey().getKey().asString(), entry.getValue());
            }
            unbreakable = meta.isUnbreakable();
            if (meta.hasDisplayName()) {
                customName = plainText(meta.displayName());
            } else if (meta.hasItemName()) {
                customName = plainText(meta.itemName());
            }
            if (meta instanceof Damageable damageable && damageable.hasDamage()) {
                damage = damageable.getDamage();
            }
        }

        return new ItemDescriptor(
                itemStack.getType().getKey().asString(),
                itemStack.getAmount(),
                enchantments,
                customName,
                unbreakable,
                damage,
                itemStack.getType().getMaxDurability(),
                identifyingProviderId
        );
    }

    /**
     * Human readable name for an item, falling back to its translation key.
     */
    public static String displayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "minecraft:air";
        }

        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta.hasDisplayName()) {
                String plain = plainText(meta.displayName());
                if (!plain.isBlank()) {
                    return plain;
                }
            }
            if (meta.hasItemName()) {
                String plain = plainText(meta.itemName());
                if (!plain.isBlank()) {
                    return plain;
                }
            }
        }

        return itemStack.getType().translationKey();
    }

    private static String plainText(Component component) {
        return component == null
                ? ""
                : PlainTextComponentSerializer.plainText().serialize(component);
    }
}
