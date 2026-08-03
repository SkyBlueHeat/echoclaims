package io.github.skyblueheat.echoclaims.integration.vanilla;

import io.github.skyblueheat.echoclaims.domain.content.Capability;
import io.github.skyblueheat.echoclaims.domain.content.ContentKey;
import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;
import io.github.skyblueheat.echoclaims.domain.content.SemanticRole;
import io.github.skyblueheat.echoclaims.integration.ItemContentProvider;
import io.github.skyblueheat.echoclaims.integration.ProviderHealth;
import io.github.skyblueheat.echoclaims.integration.bukkit.BukkitItems;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Fallback provider that maps any Bukkit item stack to semantic roles and capabilities.
 */
public final class VanillaItemProvider implements ItemContentProvider {

    public static final String PROVIDER_ID = "vanilla";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public ProviderHealth health() {
        return ProviderHealth.AVAILABLE;
    }

    @Override
    public boolean supports(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir();
    }

    @Override
    public Optional<IdentifiedContent> identify(ItemStack itemStack) {
        if (!supports(itemStack)) {
            return Optional.empty();
        }

        Material material = itemStack.getType();
        Set<SemanticRole> roles = EnumSet.noneOf(SemanticRole.class);
        Set<Capability> capabilities = EnumSet.of(
                Capability.CAN_HAVE_HISTORY,
                Capability.CAN_CHANGE_OWNER,
                Capability.CAN_BE_STOLEN,
                Capability.CAN_BE_LOST
        );

        if (Tag.ITEMS_SWORDS.isTagged(material) || Tag.ITEMS_AXES.isTagged(material)) {
            roles.add(SemanticRole.WEAPON);
        }

        if (Tag.ITEMS_HEAD_ARMOR.isTagged(material)
                || Tag.ITEMS_CHEST_ARMOR.isTagged(material)
                || Tag.ITEMS_LEG_ARMOR.isTagged(material)
                || Tag.ITEMS_FOOT_ARMOR.isTagged(material)) {
            roles.add(SemanticRole.ARMOR);
        }

        if (material.getMaxDurability() > 0) {
            capabilities.add(Capability.CAN_BECOME_HEIRLOOM);
        }

        return Optional.of(new IdentifiedContent(
                new ContentKey(PROVIDER_ID, material.getKey().asString()),
                BukkitItems.displayName(itemStack),
                roles,
                capabilities
        ));
    }
}
