package dev.worldecho.application.memory;

import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.memory.Attributes;
import dev.worldecho.domain.memory.MemoryEventType;
import dev.worldecho.domain.memory.StoryMemoryEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Turns a {@link DeathCapture} into the immutable record that is persisted.
 */
public final class DeathMemoryFactory {

    private final Supplier<UUID> idSupplier;
    private final boolean redactCustomItemNames;

    public DeathMemoryFactory(boolean redactCustomItemNames) {
        this(UUID::randomUUID, redactCustomItemNames);
    }

    public DeathMemoryFactory(Supplier<UUID> idSupplier, boolean redactCustomItemNames) {
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
        this.redactCustomItemNames = redactCustomItemNames;
    }

    public StoryMemoryEvent create(DeathCapture capture) {
        Objects.requireNonNull(capture, "capture");

        ContentKey itemKey = capture.loot() == null ? null : capture.loot().content().key();
        String snapshot = capture.loot() == null ? "" : snapshot(capture.loot());

        String details = Attributes.create()
                .put("world", capture.worldName())
                .put("killer", capture.killer().key().toString())
                .put("killerRoles", capture.killer().roles().toString())
                .put("itemScore", capture.loot() == null ? 0 : capture.loot().score().value())
                .put("inspectedDrops", capture.inspectedDropCount())
                .encode();

        return new StoryMemoryEvent(
                idSupplier.get(),
                MemoryEventType.PLAYER_KILLED_BY_ENTITY,
                capture.occurredAt(),
                capture.worldId(),
                capture.x(),
                capture.y(),
                capture.z(),
                capture.playerId(),
                capture.killer().key(),
                capture.killerRuntimeId() == null ? "" : capture.killerRuntimeId().toString(),
                itemKey,
                snapshot,
                details
        );
    }

    private String snapshot(LootCandidate loot) {
        ItemDescriptor descriptor = loot.descriptor();
        String customName = redactCustomItemNames || !descriptor.hasCustomName()
                ? ""
                : descriptor.customName();

        String trackedId = loot.optionalTrackedItemId()
                .map(TrackedItemId::toString)
                .orElse("");

        return Attributes.create()
                .put("content", loot.content().key().toString())
                .put("material", descriptor.materialKey())
                .put("amount", descriptor.amount())
                .put("customName", customName)
                .put("redacted", Boolean.toString(redactCustomItemNames && descriptor.hasCustomName()))
                .put("enchantments", descriptor.enchantments().toString())
                .put("unbreakable", Boolean.toString(descriptor.unbreakable()))
                .put("damage", descriptor.damage())
                .put("score", loot.score().value())
                .put("scoreFactors", loot.score().explain())
                .put("trackedItemId", trackedId)
                .encode();
    }
}
