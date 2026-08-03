package io.github.skyblueheat.echoclaims.integration.vanilla;

import io.github.skyblueheat.echoclaims.domain.content.Capability;
import io.github.skyblueheat.echoclaims.domain.content.ContentKey;
import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;
import io.github.skyblueheat.echoclaims.domain.content.SemanticRole;
import io.github.skyblueheat.echoclaims.integration.EntityContentProvider;
import io.github.skyblueheat.echoclaims.integration.ProviderHealth;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Fallback provider that maps any Bukkit entity to semantic roles and capabilities.
 *
 * <p>It always answers last so a bridge such as MythicMobs can claim the entity first.</p>
 */
public final class VanillaEntityProvider implements EntityContentProvider {

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
    public boolean supports(Entity entity) {
        return entity != null;
    }

    @Override
    public Optional<IdentifiedContent> identify(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }

        Set<SemanticRole> roles = EnumSet.noneOf(SemanticRole.class);
        Set<Capability> capabilities = EnumSet.noneOf(Capability.class);

        switch (entity) {
            case Player ignored -> {
                roles.add(SemanticRole.PLAYER);
                capabilities.addAll(Set.of(
                        Capability.CAN_FIGHT,
                        Capability.CAN_HOLD_ITEMS,
                        Capability.CAN_OWN_ITEMS
                ));
            }
            case Villager ignored -> {
                roles.add(SemanticRole.CIVILIAN);
                capabilities.addAll(Set.of(
                        Capability.CAN_TRADE,
                        Capability.CAN_HOLD_ITEMS
                ));
            }
            case Monster ignored -> {
                roles.add(SemanticRole.MONSTER);
                capabilities.addAll(Set.of(
                        Capability.CAN_FIGHT,
                        Capability.CAN_HOLD_ITEMS
                ));
            }
            case Animals ignored -> roles.add(SemanticRole.ANIMAL);
            default -> {
            }
        }

        if (entity instanceof LivingEntity) {
            capabilities.add(Capability.CAN_FIGHT);
        }
        if (entity instanceof Mob) {
            capabilities.add(Capability.CAN_HAVE_HISTORY);
        }

        return Optional.of(new IdentifiedContent(
                new ContentKey(PROVIDER_ID, entity.getType().getKey().asString()),
                displayName(entity),
                roles,
                capabilities
        ));
    }

    private String displayName(Entity entity) {
        Component customName = entity.customName();
        if (customName != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(customName);
            if (!plain.isBlank()) {
                return plain;
            }
        }
        return entity.getType().translationKey();
    }
}
