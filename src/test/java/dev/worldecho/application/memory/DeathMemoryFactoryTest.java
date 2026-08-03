package dev.worldecho.application.memory;

import dev.worldecho.application.ItemValueScorer;
import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.content.SemanticRole;
import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.ItemScoreWeights;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.memory.Attributes;
import dev.worldecho.domain.memory.MemoryEventType;
import dev.worldecho.domain.memory.StoryMemoryEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathMemoryFactoryTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final Instant WHEN = Instant.parse("2026-07-29T12:00:00Z");

    private final ItemValueScorer scorer = new ItemValueScorer(ItemScoreWeights.defaults());

    @Test
    void mapsKillerLocationAndItemIntoAnImmutableEvent() {
        StoryMemoryEvent event = factory(false).create(capture(loot("Kral Kılıcı")));

        assertEquals(EVENT_ID, event.id());
        assertEquals(MemoryEventType.PLAYER_KILLED_BY_ENTITY, event.type());
        assertEquals(WHEN, event.occurredAt());
        assertEquals(new ContentKey("mythicmobs", "shadow_warden"), event.actor());
        assertEquals(new ContentKey("oraxen", "kings_blade"), event.item());
        assertEquals(-100, event.x());
        assertEquals(70, event.y());
        assertEquals(240, event.z());
    }

    @Test
    void snapshotExplainsWhyTheItemWasSelected() {
        StoryMemoryEvent event = factory(false).create(capture(loot("Kral Kılıcı")));
        Map<String, String> snapshot = Attributes.decode(event.itemSnapshot());

        assertEquals("minecraft:netherite_sword", snapshot.get("material"));
        assertEquals("Kral Kılıcı", snapshot.get("customName"));
        assertEquals("false", snapshot.get("redacted"));
        assertTrue(snapshot.get("scoreFactors").contains("material="));
        assertEquals(
                snapshot.get("score"),
                Attributes.decode(event.details()).get("itemScore")
        );
    }

    @Test
    void customNamesCanBeRedacted() {
        StoryMemoryEvent event = factory(true).create(capture(loot("Kral Kılıcı")));
        Map<String, String> snapshot = Attributes.decode(event.itemSnapshot());

        assertEquals("", snapshot.get("customName"));
        assertEquals("true", snapshot.get("redacted"));
        assertFalse(event.itemSnapshot().contains("Kral"));
    }

    @Test
    void separatorCharactersInNamesCannotCorruptTheSnapshot() {
        StoryMemoryEvent event = factory(false).create(capture(loot("a=b;c=d")));

        assertEquals("a=b;c=d", Attributes.decode(event.itemSnapshot()).get("customName"));
        assertEquals("minecraft:netherite_sword",
                Attributes.decode(event.itemSnapshot()).get("material"));
    }

    @Test
    void deathsWithoutLootStillProduceAnExplainableMemory() {
        StoryMemoryEvent event = factory(false).create(capture(null));

        assertTrue(event.optionalItem().isEmpty());
        assertEquals("", event.itemSnapshot());
        assertEquals("0", Attributes.decode(event.details()).get("itemScore"));
        assertEquals("world_nether", Attributes.decode(event.details()).get("world"));
    }

    @Test
    void trackedItemIdIncludedInSnapshotWhenPresent() {
        TrackedItemId trackedId = TrackedItemId.random();
        LootCandidate candidate = lootWithTrackedId(trackedId);
        StoryMemoryEvent event = factory(false).create(capture(candidate));

        Map<String, String> snapshot = Attributes.decode(event.itemSnapshot());
        assertEquals(trackedId.toString(), snapshot.get("trackedItemId"));
    }

    @Test
    void trackedItemIdEmptyWhenNotTracked() {
        StoryMemoryEvent event = factory(false).create(capture(loot("Kral Kılıcı")));

        Map<String, String> snapshot = Attributes.decode(event.itemSnapshot());
        assertEquals("", snapshot.get("trackedItemId"));
    }

    private DeathMemoryFactory factory(boolean redact) {
        return new DeathMemoryFactory(() -> EVENT_ID, redact);
    }

    private DeathCapture capture(LootCandidate loot) {
        return new DeathCapture(
                WHEN,
                UUID.fromString("00000000-0000-0000-0000-00000000000a"),
                "world_nether",
                -100, 70, 240,
                UUID.fromString("00000000-0000-0000-0000-00000000000b"),
                new IdentifiedContent(
                        new ContentKey("mythicmobs", "shadow_warden"),
                        "Shadow Warden",
                        Set.of(SemanticRole.MONSTER),
                        Set.of(Capability.CAN_FIGHT)
                ),
                UUID.fromString("00000000-0000-0000-0000-00000000000c"),
                loot,
                7
        );
    }

    private LootCandidate loot(String customName) {
        ItemDescriptor descriptor = new ItemDescriptor(
                "minecraft:netherite_sword",
                1,
                Map.of("minecraft:sharpness", 5),
                customName,
                true,
                0,
                2031,
                "oraxen"
        );

        return new LootCandidate(
                new IdentifiedContent(
                        new ContentKey("oraxen", "kings_blade"),
                        customName,
                        Set.of(SemanticRole.WEAPON),
                        Set.of(Capability.CAN_BECOME_HEIRLOOM)
                ),
                descriptor,
                scorer.score(descriptor)
        );
    }

    private LootCandidate lootWithTrackedId(TrackedItemId trackedId) {
        ItemDescriptor descriptor = new ItemDescriptor(
                "minecraft:netherite_sword",
                1,
                Map.of("minecraft:sharpness", 5),
                "Tracked Sword",
                true,
                0,
                2031,
                "oraxen"
        );

        return new LootCandidate(
                new IdentifiedContent(
                        new ContentKey("oraxen", "kings_blade"),
                        "Tracked Sword",
                        Set.of(SemanticRole.WEAPON),
                        Set.of(Capability.CAN_BECOME_HEIRLOOM)
                ),
                descriptor,
                scorer.score(descriptor),
                trackedId
        );
    }
}
