package dev.worldecho.application;

import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.ItemScore;
import dev.worldecho.domain.item.ItemScoreWeights;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemValueScorerTest {

    private final ItemValueScorer scorer = new ItemValueScorer(ItemScoreWeights.defaults());

    @Test
    void ordersMaterialTiersFromWoodToNetherite() {
        int wooden = score("minecraft:wooden_sword");
        int stone = score("minecraft:stone_sword");
        int iron = score("minecraft:iron_sword");
        int diamond = score("minecraft:diamond_sword");
        int netherite = score("minecraft:netherite_sword");

        assertTrue(wooden < stone, "wood should score below stone");
        assertTrue(stone < iron, "stone should score below iron");
        assertTrue(iron < diamond, "iron should score below diamond");
        assertTrue(diamond < netherite, "diamond should score below netherite");
    }

    @Test
    void exactMaterialScoreOverridesTierPrefix() {
        // golden_ tier is 25, but the exact enchanted_golden_apple entry is 60.
        assertTrue(score("minecraft:enchanted_golden_apple") > score("minecraft:golden_sword"));
    }

    @Test
    void enchantmentsCustomNameAndUnbreakableIncreaseValue() {
        ItemDescriptor plain = ItemDescriptor.of("minecraft:diamond_sword", 1);
        ItemDescriptor enriched = new ItemDescriptor(
                "minecraft:diamond_sword",
                1,
                Map.of("minecraft:sharpness", 5),
                "Kral Kılıcı",
                true,
                0,
                1561,
                "vanilla"
        );

        assertTrue(scorer.score(enriched).value() > scorer.score(plain).value());
    }

    @Test
    void wearReducesValueWithoutGoingNegative() {
        ItemDescriptor pristine = new ItemDescriptor(
                "minecraft:wooden_shovel", 1, Map.of(), "", false, 0, 59, "");
        ItemDescriptor broken = new ItemDescriptor(
                "minecraft:wooden_shovel", 1, Map.of(), "", false, 59, 59, "");

        assertTrue(scorer.score(broken).value() < scorer.score(pristine).value());
        assertEquals(0, scorer.score(broken).value());
    }

    @Test
    void nonVanillaProviderAddsIdentificationBonus() {
        ItemDescriptor vanilla = new ItemDescriptor(
                "minecraft:iron_sword", 1, Map.of(), "", false, 0, 250, "vanilla");
        ItemDescriptor custom = new ItemDescriptor(
                "minecraft:iron_sword", 1, Map.of(), "", false, 0, 250, "oraxen");

        assertEquals(
                ItemScoreWeights.defaults().providerIdentifiedBonus(),
                scorer.score(custom).value() - scorer.score(vanilla).value()
        );
    }

    @Test
    void customItemsAreNotAutomaticallyValuable() {
        ItemDescriptor customDirt = new ItemDescriptor(
                "minecraft:dirt", 1, Map.of(), "", false, 0, 0, "itemsadder");

        assertTrue(customDirt.identifyingProviderId().equals("itemsadder"));
        assertTrue(scorer.score(customDirt).value() < score("minecraft:diamond_sword"));
    }

    @Test
    void scoringIsDeterministicAndExplained() {
        ItemDescriptor descriptor = new ItemDescriptor(
                "minecraft:netherite_axe",
                1,
                Map.of("minecraft:efficiency", 4),
                "",
                false,
                100,
                2031,
                "vanilla"
        );

        ItemScore first = scorer.score(descriptor);
        ItemScore second = scorer.score(descriptor);

        assertEquals(first, second);
        assertFalse(first.explain().isBlank());
        assertTrue(first.explain().contains("material="));
    }

    @Test
    void weightsAreConfigurable() {
        ItemValueScorer cheapNetherite = new ItemValueScorer(new ItemScoreWeights(
                Map.of(), Map.of("netherite_", 1), 1, 0, 0, 0, 0, 0, 0, 0));

        assertEquals(1, cheapNetherite.score(
                ItemDescriptor.of("minecraft:netherite_sword", 1)).value());
    }

    private int score(String materialKey) {
        return scorer.score(ItemDescriptor.of(materialKey, 1)).value();
    }
}
