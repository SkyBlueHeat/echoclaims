package dev.worldecho.domain.item;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic fingerprint representing stacking-relevant content for lot compatibility.
 *
 * <p>Two stacks with the same fingerprint are compatible for merge purposes.
 * Amount is deliberately excluded: 16 and 32 cobblestone have the same fingerprint.
 */
public record LotCompatibilityFingerprint(
        String providerId,
        String contentKeyId,
        String material,
        int damageValue,
        String potionVariant,
        String bookState,
        String mapState,
        String fireworkState,
        String trimState,
        Map<String, Integer> enchantments,
        int customModelData,
        String providerMetadataHash
) {

    public LotCompatibilityFingerprint {
        providerId = normalize(providerId);
        contentKeyId = normalize(contentKeyId);
        material = normalize(material);
        potionVariant = normalize(potionVariant);
        bookState = normalize(bookState);
        mapState = normalize(mapState);
        fireworkState = normalize(fireworkState);
        trimState = normalize(trimState);
        providerMetadataHash = normalize(providerMetadataHash);
        damageValue = Math.max(0, damageValue);
        customModelData = Math.max(0, customModelData);
        enchantments = enchantments == null ? Map.of() : Map.copyOf(new TreeMap<>(enchantments));
    }

    /**
     * Deterministic serialization for storage and comparison.
     */
    public String serialize() {
        return String.join("|",
                providerId,
                contentKeyId,
                material,
                Integer.toString(damageValue),
                potionVariant,
                bookState,
                mapState,
                fireworkState,
                trimState,
                serializeEnchantments(),
                Integer.toString(customModelData),
                providerMetadataHash
        );
    }

    private String serializeEnchantments() {
        if (enchantments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    public boolean isCompatibleWith(LotCompatibilityFingerprint other) {
        return this.equals(other);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public static final class Builder {
        private String providerId = "";
        private String contentKeyId = "";
        private String material = "";
        private int damageValue;
        private String potionVariant = "";
        private String bookState = "";
        private String mapState = "";
        private String fireworkState = "";
        private String trimState = "";
        private Map<String, Integer> enchantments = Map.of();
        private int customModelData;
        private String providerMetadataHash = "";

        public Builder providerId(String v) { this.providerId = v; return this; }
        public Builder contentKeyId(String v) { this.contentKeyId = v; return this; }
        public Builder material(String v) { this.material = v; return this; }
        public Builder damageValue(int v) { this.damageValue = v; return this; }
        public Builder potionVariant(String v) { this.potionVariant = v; return this; }
        public Builder bookState(String v) { this.bookState = v; return this; }
        public Builder mapState(String v) { this.mapState = v; return this; }
        public Builder fireworkState(String v) { this.fireworkState = v; return this; }
        public Builder trimState(String v) { this.trimState = v; return this; }
        public Builder enchantments(Map<String, Integer> v) { this.enchantments = v; return this; }
        public Builder customModelData(int v) { this.customModelData = v; return this; }
        public Builder providerMetadataHash(String v) { this.providerMetadataHash = v; return this; }

        public LotCompatibilityFingerprint build() {
            return new LotCompatibilityFingerprint(
                    providerId, contentKeyId, material, damageValue,
                    potionVariant, bookState, mapState, fireworkState, trimState,
                    enchantments, customModelData, providerMetadataHash
            );
        }
    }
}
