package dev.worldecho.paper.listener;

import dev.worldecho.config.WorldEchoSettings;
import dev.worldecho.paper.inventory.PlayerInventoryReconciliationScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.function.Supplier;

/**
 * Listens for events that may change a player's inventory and schedules
 * final-state reconciliation on the next tick.
 *
 * <p>Covers: join, respawn, inventory click, inventory drag, pickup, death,
 * drop, world change, crafting result, furnace extract, offhand swap,
 * fishing. Creative inventory actions and merchant trades are covered by
 * InventoryClickEvent. Hotbar number-key swaps are covered by
 * InventoryClickEvent with HOTBAR_SWAP action. pre-mutation events.
 * Only marks the player as pending and lets the scheduler coalesce.
 */
public final class PlayerInventoryObservationListener implements Listener {

    private final Supplier<WorldEchoSettings> settingsSupplier;
    private final PlayerInventoryReconciliationScheduler scheduler;

    public PlayerInventoryObservationListener(
            Supplier<WorldEchoSettings> settingsSupplier,
            PlayerInventoryReconciliationScheduler scheduler
    ) {
        this.settingsSupplier = settingsSupplier;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileOnJoin()) {
            return;
        }
        scheduler.scheduleReconciliation(event.getPlayer().getUniqueId(), "join");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileOnRespawn()) {
            return;
        }
        scheduler.scheduleReconciliation(event.getPlayer().getUniqueId(), "respawn");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            scheduler.scheduleReconciliation(player.getUniqueId(), "inventory-click");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            scheduler.scheduleReconciliation(player.getUniqueId(), "inventory-drag");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            scheduler.scheduleReconciliation(player.getUniqueId(), "pickup");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        scheduler.scheduleReconciliation(event.getEntity().getUniqueId(), "death");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        scheduler.scheduleReconciliation(event.getPlayer().getUniqueId(), "drop");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        scheduler.scheduleReconciliation(event.getPlayer().getUniqueId(), "world-change");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            scheduler.scheduleReconciliation(player.getUniqueId(), "crafting");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        scheduler.scheduleReconciliation(event.getPlayer().getUniqueId(), "furnace-extract");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        scheduler.scheduleReconciliation(event.getPlayer().getUniqueId(), "offhand-swap");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!isAutomaticTrackingEnabled()) {
            return;
        }
        if (!settingsSupplier.get().automaticTrackingReconcileAfterInventoryEvents()) {
            return;
        }
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Player player = event.getPlayer();
            if (player != null) {
                scheduler.scheduleReconciliation(player.getUniqueId(), "fishing");
            }
        }
    }

    private boolean isAutomaticTrackingEnabled() {
        WorldEchoSettings settings = settingsSupplier.get();
        return settings.automaticTrackingEnabled()
                && settings.automaticTrackingPlayerInventories();
    }
}
