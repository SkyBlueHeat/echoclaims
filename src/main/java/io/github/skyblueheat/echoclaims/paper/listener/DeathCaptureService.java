package io.github.skyblueheat.echoclaims.paper.listener;

import io.github.skyblueheat.echoclaims.application.DeduplicationKeyFactory;
import io.github.skyblueheat.echoclaims.application.EvidencePersistenceService;
import io.github.skyblueheat.echoclaims.application.ItemValueScorer;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.domain.content.IdentifiedContent;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import io.github.skyblueheat.echoclaims.integration.ItemSerializationException;
import io.github.skyblueheat.echoclaims.integration.ItemSerializer;
import io.github.skyblueheat.echoclaims.integration.IntegrationRegistry;
import io.github.skyblueheat.echoclaims.integration.bukkit.BukkitItems;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures player-death evidence on the Paper main thread and submits it for
 * asynchronous persistence.
 *
 * <p>All Bukkit objects are read during event handling and immediately converted into
 * immutable domain records. No mutable Bukkit object is retained or passed to
 * asynchronous tasks.</p>
 */
public final class DeathCaptureService implements Listener {

    private static final int SLOT_OFFSET_ARMOR = 36;

    private final Supplier<EchoClaimsSettings> settingsSupplier;
    private final ItemSerializer itemSerializer;
    private final ItemValueScorer itemValueScorer;
    private final IntegrationRegistry integrations;
    private final EvidencePersistenceService persistenceService;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, PendingDeath> pendingRespawns = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;

    public DeathCaptureService(
            Supplier<EchoClaimsSettings> settingsSupplier,
            ItemSerializer itemSerializer,
            ItemValueScorer itemValueScorer,
            IntegrationRegistry integrations,
            EvidencePersistenceService persistenceService,
            Logger logger
    ) {
        this.settingsSupplier = settingsSupplier;
        this.itemSerializer = itemSerializer;
        this.itemValueScorer = itemValueScorer;
        this.integrations = integrations;
        this.persistenceService = persistenceService;
        this.logger = logger;
    }

    public void disable() {
        enabled = false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }

        EchoClaimsSettings settings = settingsSupplier.get();
        if (!settings.capturePlayerDeath()) {
            return;
        }

        try {
            captureDeath(event, settings);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "Failed to capture death evidence for " + event.getEntity().getName(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!enabled) {
            return;
        }

        EchoClaimsSettings settings = settingsSupplier.get();
        if (!settings.capturePostRespawn()) {
            return;
        }

        Player player = event.getPlayer();
        PendingDeath pending = pendingRespawns.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }

        try {
            capturePostRespawn(player, pending, settings);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "Failed to capture post-respawn evidence for " + player.getName(), exception);
        }
    }

    private void captureDeath(PlayerDeathEvent event, EchoClaimsSettings settings) {
        Player player = event.getEntity();
        long now = System.currentTimeMillis();
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        InventorySnapshot snapshot = buildSnapshot(
                player, snapshotId, CaptureReason.PRE_DEATH, now, settings.maxItemPayloadBytes()
        );

        String deathCause = determineCause(player);
        String snapshotFingerprint = DeduplicationKeyFactory.snapshotFingerprint(snapshot);

        Incident incident = buildIncident(player, event, incidentId, snapshotId, now,
                deathCause, snapshotFingerprint);

        boolean accepted = persistenceService.submitDeathCapture(snapshot, incident);
        if (!accepted) {
            logger.warning("Death evidence capture rejected for player " + player.getUniqueId());
            return;
        }

        if (settings.capturePostRespawn()) {
            pendingRespawns.put(player.getUniqueId(), new PendingDeath(incidentId, now));
        }
    }

    private void capturePostRespawn(Player player, PendingDeath pending, EchoClaimsSettings settings) {
        if (!player.isOnline()) {
            return;
        }

        UUID snapshotId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        InventorySnapshot snapshot = buildSnapshot(
                player, snapshotId, CaptureReason.POST_RESPAWN, now, settings.maxItemPayloadBytes()
        );

        persistenceService.submitPostRespawnSnapshot(pending.incidentId(), snapshot);
    }

    private InventorySnapshot buildSnapshot(
            Player player,
            UUID snapshotId,
            CaptureReason reason,
            long timestamp,
            int maxPayloadBytes
    ) {
        PlayerInventory inventory = player.getInventory();
        Location location = player.getLocation();
        World world = location.getWorld();
        String worldId = world != null ? world.getName() : "unknown";
        Coordinates coordinates = new Coordinates(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        GameMode gameMode = player.getGameMode();

        List<SnapshotItem> inventoryItems = captureInventorySlots(
                inventory.getStorageContents(), 0, maxPayloadBytes
        );
        List<SnapshotItem> armorItems = captureArmorSlots(
                inventory.getArmorContents(), maxPayloadBytes
        );
        SnapshotItem offhandItem = captureSingleItem(
                inventory.getItemInOffHand(), "40", maxPayloadBytes
        );
        SnapshotItem cursorItem = captureCursorItem(player, maxPayloadBytes);

        return new InventorySnapshot(
                snapshotId,
                player.getUniqueId(),
                reason,
                timestamp,
                worldId,
                coordinates,
                gameMode.name().toLowerCase(java.util.Locale.ROOT),
                player.getHealth(),
                player.getFoodLevel(),
                player.getLevel(),
                player.getTotalExperience(),
                inventoryItems,
                armorItems,
                offhandItem,
                cursorItem,
                InventorySnapshot.currentSchemaVersion()
        );
    }

    private Incident buildIncident(
            Player player,
            PlayerDeathEvent event,
            UUID incidentId,
            UUID snapshotId,
            long timestamp,
            String deathCause,
            String snapshotFingerprint
    ) {
        Location location = player.getLocation();
        World world = location.getWorld();
        String worldId = world != null ? world.getName() : "unknown";
        Coordinates coordinates = new Coordinates(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        UUID killerPlayerUuid = null;
        String killerEntityKey = "";

        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent entityDamage) {
            Entity damager = entityDamage.getDamager();
            if (damager instanceof Player killerPlayer) {
                killerPlayerUuid = killerPlayer.getUniqueId();
            } else if (damager != null) {
                killerEntityKey = damager.getType().getKey().asString();
            }
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        boolean keepInventory = false;
        if (world != null) {
            try {
                keepInventory = Boolean.parseBoolean(world.getGameRuleValue("keepInventory"));
            } catch (IllegalArgumentException ignored) {
                // Gamerule may not exist in this world or API changed
            }
        }
        metadata.put("keepInventory", String.valueOf(keepInventory));
        metadata.put("lastDamageCause",
                lastDamage != null ? lastDamage.getCause().name() : "UNKNOWN");
        metadata.put("dropsCount", String.valueOf(event.getDrops().size()));

        String dropsSummary = summarizeDrops(event.getDrops());
        if (!dropsSummary.isEmpty()) {
            metadata.put("dropsSummary", dropsSummary);
        }

        String deduplicationKey = DeduplicationKeyFactory.forPlayerDeath(
                player.getUniqueId(), timestamp, worldId, coordinates,
                deathCause, snapshotFingerprint
        );

        return new Incident(
                incidentId,
                IncidentType.PLAYER_DEATH,
                player.getUniqueId(),
                timestamp,
                worldId,
                coordinates,
                deathCause,
                killerPlayerUuid,
                killerEntityKey,
                snapshotId,
                null,
                IncidentStatus.OPEN,
                metadata,
                deduplicationKey
        );
    }

    private List<SnapshotItem> captureInventorySlots(
            ItemStack[] contents, int slotOffset, int maxPayloadBytes
    ) {
        List<SnapshotItem> items = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            SnapshotItem item = captureSingleItem(
                    contents[i], String.valueOf(slotOffset + i), maxPayloadBytes
            );
            if (item != null) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private List<SnapshotItem> captureArmorSlots(
            ItemStack[] armorContents, int maxPayloadBytes
    ) {
        List<SnapshotItem> items = new ArrayList<>();
        for (int i = 0; i < armorContents.length; i++) {
            SnapshotItem item = captureSingleItem(
                    armorContents[i], String.valueOf(SLOT_OFFSET_ARMOR + i), maxPayloadBytes
            );
            if (item != null) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private SnapshotItem captureSingleItem(
            ItemStack itemStack, String slot, int maxPayloadBytes
    ) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }

        try {
            return convertItem(itemStack, slot, maxPayloadBytes);
        } catch (ItemSerializationException exception) {
            logger.log(Level.WARNING,
                    "Failed to serialize item in slot " + slot
                            + " (material=" + itemStack.getType().getKey().asString() + ")",
                    exception);
            return null;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "Failed to capture item in slot " + slot, exception);
            return null;
        }
    }

    private SnapshotItem captureCursorItem(Player player, int maxPayloadBytes) {
        try {
            ItemStack cursor = player.getOpenInventory().getCursor();
            return captureSingleItem(cursor, "cursor", maxPayloadBytes);
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Could not read cursor item", exception);
            return null;
        }
    }

    private SnapshotItem convertItem(
            ItemStack itemStack, String slot, int maxPayloadBytes
    ) throws ItemSerializationException {
        String serializedData = "";
        try {
            serializedData = itemSerializer.serialize(itemStack);
        } catch (ItemSerializationException exception) {
            if (itemSerializer.maxPayloadBytes() != maxPayloadBytes) {
                throw exception;
            }
            throw exception;
        }

        var descriptor = BukkitItems.describe(itemStack, "");
        var score = itemValueScorer.score(descriptor);
        String displayName = BukkitItems.displayName(itemStack);

        String contentIdentity = "";
        try {
            var identified = integrations.identifyItem(itemStack);
            if (identified.isPresent()) {
                contentIdentity = identified.get().key().toString();
            }
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Content identification failed for slot " + slot, exception);
        }

        return new SnapshotItem(
                slot,
                descriptor.materialKey(),
                descriptor.amount(),
                serializedData,
                contentIdentity,
                score.value(),
                displayName,
                descriptor.damage(),
                descriptor.maxDurability(),
                descriptor.enchantments()
        );
    }

    private static String determineCause(Player player) {
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage != null) {
            return lastDamage.getCause().name();
        }
        return "UNKNOWN";
    }

    private static String summarizeDrops(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(drop.getType().getKey().asString()).append(':').append(drop.getAmount());
            count++;
            if (count >= 20) {
                builder.append("...");
                break;
            }
        }
        return builder.toString();
    }

    private record PendingDeath(UUID incidentId, long deathTime) {
    }
}
