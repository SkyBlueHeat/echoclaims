package dev.worldecho.application;

import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.ItemScore;
import dev.worldecho.domain.item.ItemScoreWeights;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, configuration-driven value estimate for a captured item.
 *
 * <p>The scorer is intentionally pure: it never touches Bukkit, so the same descriptor
 * always produces the same score and the same explanation.</p>
 */
public final class ItemValueScorer {

    private final ItemScoreWeights weights;

    public ItemValueScorer(ItemScoreWeights weights) {
        this.weights = Objects.requireNonNull(weights, "weights");
    }

    public ItemScoreWeights weights() {
        return weights;
    }

    public ItemScore score(ItemDescriptor descriptor) {
        if (descriptor == null) {
            return ItemScore.zero();
        }

        List<ItemScore.ScoreFactor> factors = new ArrayList<>();
        int total = add(factors, "material", weights.materialScore(descriptor.materialPath()));

        int enchantmentPoints = 0;
        for (Map.Entry<String, Integer> enchantment : descriptor.enchantments().entrySet()) {
            enchantmentPoints += weights.enchantmentBase()
                    + weights.enchantmentPerLevel() * enchantment.getValue();
        }
        total += add(factors, "enchantments", enchantmentPoints);

        if (descriptor.hasCustomName()) {
            total += add(factors, "custom-name", weights.customNameBonus());
        }

        if (descriptor.unbreakable()) {
            total += add(factors, "unbreakable", weights.unbreakableBonus());
        }

        if (!descriptor.identifyingProviderId().isEmpty()
                && !"vanilla".equals(descriptor.identifyingProviderId())) {
            total += add(factors, "provider", weights.providerIdentifiedBonus());
        }

        int amountBonus = Math.min(weights.amountBonusCap(), descriptor.amount() - 1);
        total += add(factors, "amount", amountBonus);

        int wearPenalty = (int) Math.round(descriptor.wear() * weights.wearPenaltyCap());
        total += add(factors, "wear", -wearPenalty);

        return new ItemScore(Math.max(0, total), factors);
    }

    private int add(List<ItemScore.ScoreFactor> factors, String name, int points) {
        if (points != 0) {
            factors.add(new ItemScore.ScoreFactor(name, points));
        }
        return points;
    }
}
