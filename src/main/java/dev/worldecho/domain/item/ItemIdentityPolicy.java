package dev.worldecho.domain.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure-Java policy that classifies an {@link ObservedItemDescriptor} as {@link IdentityMode#UNIQUE}
 * or {@link IdentityMode#LOT}.
 *
 * <p>Does not import Bukkit or Paper.  Deterministic and immutable.
 *
 * <p>When classification is uncertain, prefers {@link IdentityMode#UNIQUE} to avoid
 * losing the identity of a meaningful item.
 */
public final class ItemIdentityPolicy {

    private ItemIdentityPolicy() {
    }

    /**
     * Classifies the observed item descriptor.
     *
     * @return an immutable {@link IdentityClassificationResult}
     */
    public static IdentityClassificationResult classify(ObservedItemDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        List<String> uniqueReasons = new ArrayList<>();
        List<String> lotReasons = new ArrayList<>();

        checkUniqueConditions(descriptor, uniqueReasons);
        checkLotConditions(descriptor, lotReasons);

        if (!uniqueReasons.isEmpty()) {
            return IdentityClassificationResult.unique(uniqueReasons, 1.0);
        }

        if (!lotReasons.isEmpty() && isSafeForLot(descriptor)) {
            return IdentityClassificationResult.lot(lotReasons, 0.9);
        }

        return IdentityClassificationResult.unique(
                List.of("uncertain-classification-defaults-to-unique"), 0.5);
    }

    private static void checkUniqueConditions(ObservedItemDescriptor d, List<String> reasons) {
        if (d.maxStackSize() == 1) {
            reasons.add("max-stack-size-is-1");
        }
        if (d.isDamageable()) {
            reasons.add("damageable");
        }
        if (d.hasCustomName()) {
            reasons.add("custom-name-present");
        }
        if (d.hasLore()) {
            reasons.add("lore-present");
        }
        if (d.hasEnchantments()) {
            reasons.add("enchantments-present");
        }
        if (d.hasCustomModelData()) {
            reasons.add("custom-model-data-present");
        }
        if (d.uniqueBookOrMapState()) {
            reasons.add("unique-book-or-map-state");
        }
        if (d.meaningfulProviderPdcPresent()) {
            reasons.add("meaningful-provider-pdc");
        }
        if (d.hasExistingIdentity()) {
            reasons.add("existing-worldecho-identity");
        }
        if (d.memoryLinked()) {
            reasons.add("memory-linked");
        }
        if (d.storyLinked()) {
            reasons.add("story-linked");
        }
        if (d.isProviderUnique()) {
            reasons.add("binding-indicates-unique");
        }
        if (d.hasBindingRoles() || d.hasBindingCapabilities()) {
            if (d.bindingIndicatesUnique()) {
                reasons.add("binding-roles-capabilities-indicate-unique");
            }
        }
        if (!d.providerId().isEmpty() && !d.providerId().equals("minecraft")
                && !d.providerId().equals("vanilla") && !d.meaningfulProviderPdcPresent()) {
            if (!isSafeForLot(d)) {
                reasons.add("unknown-custom-provider-prefers-unique");
            }
        }
    }

    private static void checkLotConditions(ObservedItemDescriptor d, List<String> reasons) {
        if (d.isStackable()) {
            reasons.add("stackable");
        }
        if (!d.hasCustomName()) {
            reasons.add("no-custom-name");
        }
        if (!d.hasLore()) {
            reasons.add("no-lore");
        }
        if (!d.hasEnchantments()) {
            reasons.add("no-enchantments");
        }
        if (!d.hasCustomModelData()) {
            reasons.add("no-custom-model-data");
        }
        if (!d.uniqueBookOrMapState()) {
            reasons.add("no-unique-book-or-map");
        }
        if (!d.meaningfulProviderPdcPresent()) {
            reasons.add("no-meaningful-provider-pdc");
        }
        if (!d.hasExistingIdentity()) {
            reasons.add("no-existing-identity");
        }
        if (!d.memoryLinked()) {
            reasons.add("not-memory-linked");
        }
        if (!d.storyLinked()) {
            reasons.add("not-story-linked");
        }
    }

    private static boolean isSafeForLot(ObservedItemDescriptor d) {
        return d.isStackable()
                && !d.hasCustomName()
                && !d.hasLore()
                && !d.hasEnchantments()
                && !d.hasCustomModelData()
                && !d.uniqueBookOrMapState()
                && !d.meaningfulProviderPdcPresent()
                && !d.hasExistingIdentity()
                && !d.memoryLinked()
                && !d.storyLinked()
                && !d.bindingIndicatesUnique()
                && isKnownSafeProvider(d.providerId());
    }

    private static boolean isKnownSafeProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return true;
        }
        String normalized = providerId.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("minecraft") || normalized.equals("vanilla") || normalized.isEmpty();
    }
}
