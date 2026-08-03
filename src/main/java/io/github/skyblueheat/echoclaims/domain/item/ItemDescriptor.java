package io.github.skyblueheat.echoclaims.domain.item;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable, provider-neutral snapshot of an item stack.
 *
 * <p>Instances are created on the server thread from live Bukkit objects and are safe to
 * hand to asynchronous work because they retain no Minecraft state.</p>
 */
public record ItemDescriptor(
        String materialKey,
        int amount,
        Map<String, Integer> enchantments,
        String customName,
        boolean unbreakable,
        int damage,
        int maxDurability,
        String identifyingProviderId
) {

    public ItemDescriptor {
        materialKey = normalizeKey(materialKey);
        amount = Math.max(1, amount);
        enchantments = normalizeEnchantments(enchantments);
        customName = customName == null ? "" : customName.strip();
        damage = Math.max(0, damage);
        maxDurability = Math.max(0, maxDurability);
        identifyingProviderId = identifyingProviderId == null
                ? ""
                : identifyingProviderId.strip().toLowerCase(Locale.ROOT);
    }

    public static ItemDescriptor of(String materialKey, int amount) {
        return new ItemDescriptor(materialKey, amount, Map.of(), "", false, 0, 0, "");
    }

    public boolean hasCustomName() {
        return !customName.isEmpty();
    }

    /**
     * Fraction of the item's durability that has been used, in the range {@code [0, 1]}.
     * Items without durability always report {@code 0}.
     */
    public double wear() {
        if (maxDurability <= 0) {
            return 0.0d;
        }
        return Math.min(1.0d, (double) damage / (double) maxDurability);
    }

    /**
     * Material identifier without its namespace, for example {@code diamond_sword}.
     */
    public String materialPath() {
        int separator = materialKey.indexOf(':');
        return separator < 0 ? materialKey : materialKey.substring(separator + 1);
    }

    private static String normalizeKey(String value) {
        Objects.requireNonNull(value, "materialKey");
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("materialKey cannot be blank");
        }
        return normalized.indexOf(':') < 0 ? "minecraft:" + normalized : normalized;
    }

    private static Map<String, Integer> normalizeEnchantments(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> sorted = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            sorted.put(
                    entry.getKey().strip().toLowerCase(Locale.ROOT),
                    Math.max(0, entry.getValue())
            );
        }
        return Map.copyOf(new LinkedHashMap<>(sorted));
    }
}
