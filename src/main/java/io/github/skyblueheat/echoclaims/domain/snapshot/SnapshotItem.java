package io.github.skyblueheat.echoclaims.domain.snapshot;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable record of a single item captured in an inventory slot.
 *
 * <p>Instances are created on the server thread from live Bukkit objects and are safe to
 * hand to asynchronous persistence because they retain no Minecraft state.</p>
 *
 * <p>The {@code serializedData} field holds a project-owned, versioned serialization of the
 * full Bukkit ItemStack, sufficient for future comparison and refund execution. It must
 * not be confused with the lightweight {@code materialKey} and {@code enchantments}
 * fields, which are used for display and scoring.</p>
 */
public record SnapshotItem(
        String slot,
        String materialKey,
        int amount,
        String serializedData,
        String contentIdentity,
        int score,
        String displayName,
        int damage,
        int maxDurability,
        Map<String, Integer> enchantments
) {

    public SnapshotItem {
        slot = normalizeSlot(slot);
        materialKey = normalizeKey(materialKey);
        amount = Math.max(0, amount);
        serializedData = Objects.requireNonNullElse(serializedData, "");
        contentIdentity = contentIdentity == null ? "" : contentIdentity.strip();
        score = Math.max(0, score);
        displayName = displayName == null ? "" : displayName.strip();
        damage = Math.max(0, damage);
        maxDurability = Math.max(0, maxDurability);
        enchantments = normalizeEnchantments(enchantments);
    }

    public boolean isAir() {
        return "minecraft:air".equals(materialKey) || amount == 0;
    }

    public boolean hasContentIdentity() {
        return !contentIdentity.isEmpty();
    }

    public boolean hasDisplayName() {
        return !displayName.isEmpty();
    }

    public boolean hasEnchantments() {
        return !enchantments.isEmpty();
    }

    private static String normalizeSlot(String value) {
        Objects.requireNonNull(value, "slot");
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("slot cannot be blank");
        }
        return normalized;
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
