package io.github.skyblueheat.echoclaims.domain.item;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Administrator-tunable weights for {@link io.github.skyblueheat.echoclaims.application.ItemValueScorer}.
 *
 * <p>{@code materialScores} matches a full material path such as {@code elytra}.
 * {@code materialTiers} matches a material path prefix such as {@code netherite_}. Exact
 * matches win over prefixes so an admin can override a single item without redefining a
 * tier.</p>
 */
public record ItemScoreWeights(
        Map<String, Integer> materialScores,
        Map<String, Integer> materialTiers,
        int defaultMaterialScore,
        int enchantmentBase,
        int enchantmentPerLevel,
        int customNameBonus,
        int unbreakableBonus,
        int providerIdentifiedBonus,
        int amountBonusCap,
        int wearPenaltyCap
) {

    public ItemScoreWeights {
        materialScores = normalize(materialScores);
        materialTiers = normalize(materialTiers);
        defaultMaterialScore = Math.max(0, defaultMaterialScore);
        enchantmentBase = Math.max(0, enchantmentBase);
        enchantmentPerLevel = Math.max(0, enchantmentPerLevel);
        customNameBonus = Math.max(0, customNameBonus);
        unbreakableBonus = Math.max(0, unbreakableBonus);
        providerIdentifiedBonus = Math.max(0, providerIdentifiedBonus);
        amountBonusCap = Math.max(0, amountBonusCap);
        wearPenaltyCap = Math.max(0, wearPenaltyCap);
    }

    public static ItemScoreWeights defaults() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("dragon_egg", 100);
        scores.put("enchanted_golden_apple", 60);
        scores.put("elytra", 55);
        scores.put("nether_star", 55);
        scores.put("trident", 50);
        scores.put("mace", 50);
        scores.put("totem_of_undying", 45);

        Map<String, Integer> tiers = new LinkedHashMap<>();
        tiers.put("netherite_", 60);
        tiers.put("diamond_", 45);
        tiers.put("golden_", 25);
        tiers.put("iron_", 20);
        tiers.put("chainmail_", 15);
        tiers.put("stone_", 8);
        tiers.put("wooden_", 4);
        tiers.put("leather_", 4);

        return new ItemScoreWeights(scores, tiers, 1, 4, 2, 8, 12, 10, 5, 10);
    }

    /**
     * Base score for a material path, using exact overrides first and then the longest
     * matching tier prefix so {@code netherite_} beats a shorter competing prefix.
     */
    public int materialScore(String materialPath) {
        Integer exact = materialScores.get(materialPath);
        if (exact != null) {
            return exact;
        }

        int best = defaultMaterialScore;
        int bestPrefixLength = -1;
        for (Map.Entry<String, Integer> tier : materialTiers.entrySet()) {
            String prefix = tier.getKey();
            if (materialPath.startsWith(prefix) && prefix.length() > bestPrefixLength) {
                best = tier.getValue();
                bestPrefixLength = prefix.length();
            }
        }
        return best;
    }

    private static Map<String, Integer> normalize(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().strip().toLowerCase(Locale.ROOT);
            int separator = key.indexOf(':');
            normalized.put(
                    separator < 0 ? key : key.substring(separator + 1),
                    Math.max(0, entry.getValue())
            );
        }
        return Map.copyOf(normalized);
    }
}
