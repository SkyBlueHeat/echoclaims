package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.item.ItemDescriptor;
import io.github.skyblueheat.echoclaims.domain.item.ItemScore;
import io.github.skyblueheat.echoclaims.domain.item.ItemScoreWeights;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemValueScorerTest {

    @Test
    void scoresVanillaDiamondSword() {
        ItemValueScorer scorer = new ItemValueScorer(ItemScoreWeights.defaults());
        ItemDescriptor descriptor = ItemDescriptor.of("minecraft:diamond_sword", 1);

        ItemScore score = scorer.score(descriptor);

        assertTrue(score.value() >= 45, "diamond_sword should score at least 45, got " + score.value());
    }

    @Test
    void customNameAddsBonus() {
        ItemValueScorer scorer = new ItemValueScorer(ItemScoreWeights.defaults());
        ItemDescriptor plain = ItemDescriptor.of("minecraft:stick", 1);
        ItemDescriptor named = new ItemDescriptor("minecraft:stick", 1, Map.of(), "Excalibur", false, 0, 0, "");

        int plainScore = scorer.score(plain).value();
        int namedScore = scorer.score(named).value();

        assertEquals(plainScore + 8, namedScore);
    }

    @Test
    void enchantmentsAddPoints() {
        ItemValueScorer scorer = new ItemValueScorer(ItemScoreWeights.defaults());
        ItemDescriptor enchanted = new ItemDescriptor(
                "minecraft:diamond_sword", 1,
                Map.of("minecraft:sharpness", 5),
                "", false, 0, 0, ""
        );

        ItemScore score = scorer.score(enchanted);

        assertTrue(score.value() > 45, "enchanted diamond sword should score higher than base");
        assertTrue(score.explain().contains("enchantments"));
    }

    @Test
    void nullDescriptorReturnsZero() {
        ItemValueScorer scorer = new ItemValueScorer(ItemScoreWeights.defaults());

        assertEquals(0, scorer.score(null).value());
    }
}
