package dev.worldecho.domain.item;

import dev.worldecho.domain.content.ContentKey;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, provider-neutral descriptor of an observed item used for identity classification.
 *
 * <p>Created on the server thread from live Bukkit objects.  Safe to hand to asynchronous
 * classification logic because it retains no Minecraft state.
 */
public record ObservedItemDescriptor(
        String providerId,
        ContentKey contentKey,
        String material,
        int maxStackSize,
        int amount,
        boolean damageable,
        int damageValue,
        boolean customNamePresent,
        boolean lorePresent,
        boolean enchantmentsPresent,
        boolean customModelDataPresent,
        boolean uniqueBookOrMapState,
        boolean meaningfulProviderPdcPresent,
        boolean existingWorldEchoIdentity,
        boolean memoryLinked,
        boolean storyLinked,
        Set<String> bindingRoles,
        Set<String> bindingCapabilities,
        boolean bindingIndicatesUnique
) {

    public ObservedItemDescriptor {
        material = material == null ? "" : material.strip().toLowerCase(java.util.Locale.ROOT);
        providerId = providerId == null ? "" : providerId.strip().toLowerCase(java.util.Locale.ROOT);
        maxStackSize = Math.max(1, maxStackSize);
        amount = Math.max(1, amount);
        damageValue = Math.max(0, damageValue);
        bindingRoles = bindingRoles == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(bindingRoles));
        bindingCapabilities = bindingCapabilities == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(bindingCapabilities));
    }

    public boolean isStackable() {
        return maxStackSize > 1;
    }

    public boolean hasEnchantments() {
        return enchantmentsPresent;
    }

    public boolean hasCustomName() {
        return customNamePresent;
    }

    public boolean hasLore() {
        return lorePresent;
    }

    public boolean hasCustomModelData() {
        return customModelDataPresent;
    }

    public boolean hasExistingIdentity() {
        return existingWorldEchoIdentity;
    }

    public boolean isDamageable() {
        return damageable;
    }

    public boolean isProviderUnique() {
        return bindingIndicatesUnique;
    }

    public boolean hasBindingRoles() {
        return !bindingRoles.isEmpty();
    }

    public boolean hasBindingCapabilities() {
        return !bindingCapabilities.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String providerId = "";
        private ContentKey contentKey;
        private String material = "";
        private int maxStackSize = 64;
        private int amount = 1;
        private boolean damageable;
        private int damageValue;
        private boolean customNamePresent;
        private boolean lorePresent;
        private boolean enchantmentsPresent;
        private boolean customModelDataPresent;
        private boolean uniqueBookOrMapState;
        private boolean meaningfulProviderPdcPresent;
        private boolean existingWorldEchoIdentity;
        private boolean memoryLinked;
        private boolean storyLinked;
        private Set<String> bindingRoles = Set.of();
        private Set<String> bindingCapabilities = Set.of();
        private boolean bindingIndicatesUnique;

        public Builder providerId(String value) { this.providerId = value; return this; }
        public Builder contentKey(ContentKey value) { this.contentKey = value; return this; }
        public Builder material(String value) { this.material = value; return this; }
        public Builder maxStackSize(int value) { this.maxStackSize = value; return this; }
        public Builder amount(int value) { this.amount = value; return this; }
        public Builder damageable(boolean value) { this.damageable = value; return this; }
        public Builder damageValue(int value) { this.damageValue = value; return this; }
        public Builder customNamePresent(boolean value) { this.customNamePresent = value; return this; }
        public Builder lorePresent(boolean value) { this.lorePresent = value; return this; }
        public Builder enchantmentsPresent(boolean value) { this.enchantmentsPresent = value; return this; }
        public Builder customModelDataPresent(boolean value) { this.customModelDataPresent = value; return this; }
        public Builder uniqueBookOrMapState(boolean value) { this.uniqueBookOrMapState = value; return this; }
        public Builder meaningfulProviderPdcPresent(boolean value) { this.meaningfulProviderPdcPresent = value; return this; }
        public Builder existingWorldEchoIdentity(boolean value) { this.existingWorldEchoIdentity = value; return this; }
        public Builder memoryLinked(boolean value) { this.memoryLinked = value; return this; }
        public Builder storyLinked(boolean value) { this.storyLinked = value; return this; }
        public Builder bindingRoles(Set<String> value) { this.bindingRoles = value; return this; }
        public Builder bindingCapabilities(Set<String> value) { this.bindingCapabilities = value; return this; }
        public Builder bindingIndicatesUnique(boolean value) { this.bindingIndicatesUnique = value; return this; }

        public ObservedItemDescriptor build() {
            return new ObservedItemDescriptor(
                    providerId, contentKey, material, maxStackSize, amount,
                    damageable, damageValue, customNamePresent, lorePresent,
                    enchantmentsPresent, customModelDataPresent, uniqueBookOrMapState,
                    meaningfulProviderPdcPresent, existingWorldEchoIdentity,
                    memoryLinked, storyLinked, bindingRoles, bindingCapabilities,
                    bindingIndicatesUnique
            );
        }
    }
}
