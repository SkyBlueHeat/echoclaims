package dev.worldecho.paper.listener;

import dev.worldecho.application.ItemValueScorer;
import dev.worldecho.application.MemoryRecorder;
import dev.worldecho.application.memory.DeathCapture;
import dev.worldecho.application.memory.DeathMemoryFactory;
import dev.worldecho.application.memory.LootCandidate;
import dev.worldecho.config.WorldEchoSettings;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.ItemScore;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.integration.IntegrationRegistry;
import dev.worldecho.integration.bukkit.BukkitItems;
import dev.worldecho.paper.item.ItemIdentityAdapter;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Captures "a living entity killed a player" as a provider-neutral memory.
 *
 * <p>Everything Bukkit-related happens inside this handler on the server thread. The
 * listener produces an immutable {@link DeathCapture} and hands it to the asynchronous
 * write path; no Bukkit object is retained afterwards.</p>
 */
public final class PlayerDeathMemoryListener implements Listener {

    /**
     * Upper bound on inspected drops so an unusually large inventory cannot turn one death
     * into an expensive main-thread scan.
     */
    private static final int MAX_INSPECTED_DROPS = 64;

    private final Supplier<WorldEchoSettings> settingsSupplier;
    private final IntegrationRegistry integrations;
    private final Supplier<ItemValueScorer> scorerSupplier;
    private final MemoryRecorder memoryRecorder;
    private final ItemIdentityAdapter identityAdapter;

    public PlayerDeathMemoryListener(
            Supplier<WorldEchoSettings> settingsSupplier,
            IntegrationRegistry integrations,
            Supplier<ItemValueScorer> scorerSupplier,
            MemoryRecorder memoryRecorder,
            ItemIdentityAdapter identityAdapter
    ) {
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.integrations = Objects.requireNonNull(integrations, "integrations");
        this.scorerSupplier = Objects.requireNonNull(scorerSupplier, "scorerSupplier");
        this.memoryRecorder = Objects.requireNonNull(memoryRecorder, "memoryRecorder");
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        WorldEchoSettings settings = settingsSupplier.get();
        if (!settings.playerDeathCaptureEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (settings.isWorldIgnored(player.getWorld().getName())) {
            return;
        }

        Optional<LivingEntity> killer = resolveKiller(event.getDamageSource());
        if (killer.isEmpty()) {
            return;
        }

        Optional<IdentifiedContent> killerContent = integrations.identifyEntity(killer.get());
        if (killerContent.isEmpty()) {
            return;
        }

        List<ItemStack> drops = event.getDrops();
        int inspected = Math.min(drops.size(), MAX_INSPECTED_DROPS);
        LootCandidate loot = selectLoot(drops.subList(0, inspected), settings);

        if (loot == null && !settings.recordDeathWithoutValuableItem()) {
            return;
        }

        Location location = player.getLocation();
        DeathCapture capture = new DeathCapture(
                Instant.now(),
                player.getWorld().getUID(),
                player.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                player.getUniqueId(),
                killerContent.get(),
                killer.get().getUniqueId(),
                loot,
                inspected
        );

        memoryRecorder.record(
                new DeathMemoryFactory(settings.redactCustomItemNames()).create(capture)
        );
    }

    /**
     * Resolves the entity responsible for the kill, preferring the causing entity so a
     * projectile is attributed to its shooter.
     */
    private Optional<LivingEntity> resolveKiller(DamageSource damageSource) {
        if (damageSource == null) {
            return Optional.empty();
        }

        Entity causing = damageSource.getCausingEntity();
        if (causing instanceof LivingEntity living) {
            return Optional.of(living);
        }

        Entity direct = damageSource.getDirectEntity();
        return direct instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    private LootCandidate selectLoot(List<ItemStack> drops, WorldEchoSettings settings) {
        ItemValueScorer scorer = scorerSupplier.get();
        LootCandidate best = null;

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }

            Optional<IdentifiedContent> content = integrations.identifyItem(drop);
            if (content.isEmpty()) {
                continue;
            }

            ItemDescriptor descriptor =
                    BukkitItems.describe(drop, content.get().key().providerId());
            ItemScore score = scorer.score(descriptor);
            if (score.value() < settings.minimumItemScore()) {
                continue;
            }

            if (best == null || score.value() > best.score().value()) {
                TrackedItemId trackedId = null;
                ItemIdentityAdapter.IdentityResult identity = identityAdapter.readIdentity(drop);
                if (identity.status() == ItemIdentityAdapter.IdentityStatus.EXISTING) {
                    trackedId = identity.itemId();
                }
                best = new LootCandidate(content.get(), descriptor, score, trackedId);
            }
        }

        return best;
    }
}
