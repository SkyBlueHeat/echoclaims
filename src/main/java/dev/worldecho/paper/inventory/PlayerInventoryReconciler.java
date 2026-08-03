package dev.worldecho.paper.inventory;

import dev.worldecho.application.BindingEnricher;
import dev.worldecho.config.WorldEchoSettings;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.item.AutomaticItemIdentityService;
import dev.worldecho.domain.item.DuplicateObservationRegistry;
import dev.worldecho.domain.item.IdentityClassificationResult;
import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.ItemIdentityPolicy;
import dev.worldecho.domain.item.LotCompatibilityFingerprint;
import dev.worldecho.domain.item.ObservedInventorySlot;
import dev.worldecho.domain.item.ObservedInventorySnapshot;
import dev.worldecho.domain.item.ObservedItemDescriptor;
import dev.worldecho.domain.item.OwnershipResult;
import dev.worldecho.domain.item.OwnershipResultStatus;
import dev.worldecho.domain.item.ReconciliationCycle;
import dev.worldecho.domain.item.ReconciliationMetrics;
import dev.worldecho.domain.item.ReconciliationPlanGenerator;
import dev.worldecho.domain.item.SlotProcessResult;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TrackedItemLotId;
import dev.worldecho.integration.IntegrationRegistry;
import dev.worldecho.integration.bukkit.BukkitItems;
import dev.worldecho.paper.item.ItemIdentityAdapter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Captures an immutable inventory snapshot on the main thread and processes it
 * asynchronously.
 *
 * <p>Main-thread methods only read Bukkit state and produce immutable records.
 * Background methods only touch domain logic and persistence.
 */
public final class PlayerInventoryReconciler {

    private final Supplier<WorldEchoSettings> settingsSupplier;
    private final IntegrationRegistry integrations;
    private final Supplier<BindingEnricher> enricherSupplier;
    private final ItemIdentityAdapter identityAdapter;
    private final AutomaticItemIdentityService identityService;
    private final ReconciliationMetrics metrics;
    private final DuplicateObservationRegistry duplicateRegistry;
    private final ReconciliationPlanGenerator planGenerator = new ReconciliationPlanGenerator();
    private final AtomicLong cycleSequenceCounter = new AtomicLong(0);
    private final String serverSessionId;

    public PlayerInventoryReconciler(
            Supplier<WorldEchoSettings> settingsSupplier,
            IntegrationRegistry integrations,
            Supplier<BindingEnricher> enricherSupplier,
            ItemIdentityAdapter identityAdapter,
            AutomaticItemIdentityService identityService,
            ReconciliationMetrics metrics,
            DuplicateObservationRegistry duplicateRegistry,
            String serverSessionId
    ) {
        this.settingsSupplier = settingsSupplier;
        this.integrations = integrations;
        this.enricherSupplier = enricherSupplier;
        this.identityAdapter = identityAdapter;
        this.identityService = identityService;
        this.metrics = metrics;
        this.duplicateRegistry = duplicateRegistry;
        this.serverSessionId = serverSessionId;
    }

    /**
     * Main thread: capture an immutable snapshot of the player's inventory.
     */
    public ObservedInventorySnapshot captureSnapshot(Player player, String triggerReason, long scheduledTick) {
        WorldEchoSettings settings = settingsSupplier.get();
        PlayerInventory inv = player.getInventory();
        List<ObservedInventorySlot> slots = new ArrayList<>();

        UUID playerUuid = player.getUniqueId();
        String playerDisplayName = player.getName();

        for (int section = 0; section < 4; section++) {
            ItemStack[] items = switch (section) {
                case 0 -> inv.getContents();
                case 1 -> inv.getArmorContents();
                case 2 -> new ItemStack[]{inv.getItemInOffHand()};
                case 3 -> new ItemStack[]{inv.getItemInMainHand()};
                default -> new ItemStack[0];
            };

            String sectionName = switch (section) {
                case 0 -> "main";
                case 1 -> "armor";
                case 2 -> "offhand";
                case 3 -> "mainhand";
                default -> "unknown";
            };

            for (int i = 0; i < items.length; i++) {
                ItemStack item = items[i];
                if (item == null || item.getType().isAir()) {
                    continue;
                }

                ObservedInventorySlot slot = captureSlot(item, sectionName, i, settings);
                slots.add(slot);
            }
        }

        ReconciliationCycle cycle = ReconciliationCycle.create(
                playerUuid, triggerReason,
                cycleSequenceCounter.incrementAndGet(),
                serverSessionId, scheduledTick
        );

        return new ObservedInventorySnapshot(playerUuid, playerDisplayName, slots, cycle);
    }

    /**
     * Main thread: capture a single slot into an immutable record.
     * Also assigns PDC identity for UNIQUE items that need it.
     */
    private ObservedInventorySlot captureSlot(
            ItemStack item, String sectionName, int slotIndex,
            WorldEchoSettings settings
    ) {
        ItemDescriptor descriptor = BukkitItems.describe(item, "minecraft");
        ContentKey contentKey = ContentKey.parse("minecraft:" + descriptor.materialKey().replace("minecraft:", ""));

        ItemIdentityAdapter.IdentityResult identity = identityAdapter.readIdentity(item);
        boolean malformed = identity.status() == ItemIdentityAdapter.IdentityStatus.MALFORMED;
        TrackedItemId existingId = identity.status() == ItemIdentityAdapter.IdentityStatus.EXISTING
                ? identity.itemId() : null;

        ObservedItemDescriptor observedDescriptor = ObservedItemDescriptor.builder()
                .providerId("minecraft")
                .contentKey(contentKey)
                .material(descriptor.materialKey())
                .maxStackSize(item.getMaxStackSize())
                .amount(item.getAmount())
                .damageable(descriptor.maxDurability() > 0)
                .damageValue(descriptor.damage())
                .customNamePresent(descriptor.hasCustomName())
                .enchantmentsPresent(!descriptor.enchantments().isEmpty())
                .existingWorldEchoIdentity(existingId != null)
                .build();

        IdentityClassificationResult classification = ItemIdentityPolicy.classify(observedDescriptor);

        TrackedItemLotId existingLotId = null;
        LotCompatibilityFingerprint lotFingerprint = null;
        if (classification.isLot()) {
            lotFingerprint = LotCompatibilityFingerprint.builder()
                    .providerId("minecraft")
                    .contentKeyId(contentKey.toString())
                    .material(descriptor.materialKey())
                    .damageValue(descriptor.damage())
                    .enchantments(descriptor.enchantments())
                    .build();
        }

        if (classification.isUnique() && existingId == null && !malformed
                && settings.automaticTrackingEnabled()) {
            ItemIdentityAdapter.IdentityResult assigned = identityAdapter.ensureIdentity(item);
            if (assigned.status() == ItemIdentityAdapter.IdentityStatus.ASSIGNED) {
                existingId = assigned.itemId();
                metrics.recordIdentityAssigned();
            }
        }

        return new ObservedInventorySlot(
                sectionName, slotIndex,
                descriptor.materialKey(), item.getAmount(),
                contentKey, "minecraft", descriptor,
                classification, existingId, existingLotId,
                lotFingerprint, malformed
        );
    }

    /**
     * Background thread: process the snapshot through identity and ownership services.
     *
     * <p>Delegates plan generation to {@link ReconciliationPlanGenerator} so that
     * the decision logic is unit-testable without Bukkit.
     */
    public void processSnapshot(ObservedInventorySnapshot snapshot) {
        if (snapshot == null || snapshot.slots().isEmpty()) {
            return;
        }

        UUID playerUuid = snapshot.playerUuid();
        String playerDisplayName = snapshot.playerDisplayName();
        ReconciliationCycle cycle = snapshot.cycle();

        ReconciliationPlanGenerator.ReconciliationPlan plan = planGenerator.generatePlan(snapshot);

        // Process UNIQUE items individually
        for (ReconciliationPlanGenerator.SlotPlan slotPlan : plan.slotPlans()) {
            ObservedInventorySlot slot = slotPlan.slot();

            try {
                SlotProcessResult result;
                if (slotPlan.action() == ReconciliationPlanGenerator.SlotAction.SKIP_EMPTY
                        || slotPlan.action() == ReconciliationPlanGenerator.SlotAction.SKIP_UNCLASSIFIED) {
                    continue;
                }

                if (slotPlan.action() == ReconciliationPlanGenerator.SlotAction.PROCESS_UNIQUE_WITH_DUPLICATE_CHECK) {
                    // Check for duplicate UNIQUE identity BEFORE processing
                    String contentFingerprint = slot.material() + ":" + slot.amount();
                    DuplicateObservationRegistry.Observation observation =
                            new DuplicateObservationRegistry.Observation(
                                    slot.existingUniqueId(),
                                    playerUuid,
                                    slot.inventorySection(),
                                    slot.slotIndex(),
                                    contentFingerprint,
                                    cycle.cycleSequence(),
                                    System.currentTimeMillis()
                            );
                    DuplicateObservationRegistry.DuplicateDiagnostic diagnostic =
                            duplicateRegistry.observe(observation);
                    if (diagnostic != null) {
                        metrics.recordDuplicateIdentity();
                        result = SlotProcessResult.skipped(slot);
                    } else {
                        result = identityService.processUniqueSlot(slot, playerUuid, playerDisplayName, cycle);
                    }
                } else if (slotPlan.action() == ReconciliationPlanGenerator.SlotAction.PROCESS_UNIQUE) {
                    result = identityService.processUniqueSlot(slot, playerUuid, playerDisplayName, cycle);
                } else {
                    continue;
                }

                if (result.status() == SlotProcessResult.Status.MALFORMED) {
                    metrics.recordIdentityWarning();
                } else if (result.status() == SlotProcessResult.Status.PERSISTENCE_FAILURE) {
                    metrics.recordIdentityWarning();
                }

                if (result.optionalOwnershipResult().isPresent()) {
                    OwnershipResult ownershipResult = result.optionalOwnershipResult().get();
                    if (ownershipResult.status() == OwnershipResultStatus.RECORDED) {
                        metrics.recordOwnershipTransition();
                    }
                }
            } catch (Exception exception) {
                metrics.recordIdentityWarning();
            }
        }

        // Process LOT items as aggregated groups (one update per owner+fingerprint)
        List<SlotProcessResult> lotResults = identityService.processLotSlots(snapshot);
        for (SlotProcessResult lotResult : lotResults) {
            if (lotResult.status() == SlotProcessResult.Status.PROCESSED
                    || lotResult.status() == SlotProcessResult.Status.ASSIGNED) {
                metrics.recordLotAssigned();
            }
            if (lotResult.status() == SlotProcessResult.Status.MALFORMED
                    || lotResult.status() == SlotProcessResult.Status.PERSISTENCE_FAILURE) {
                metrics.recordIdentityWarning();
            }
            if (lotResult.optionalOwnershipResult().isPresent()) {
                OwnershipResult ownershipResult = lotResult.optionalOwnershipResult().get();
                if (ownershipResult.status() == OwnershipResultStatus.RECORDED) {
                    metrics.recordOwnershipTransition();
                }
            }
        }

        metrics.recordReconciliation();
    }
}
