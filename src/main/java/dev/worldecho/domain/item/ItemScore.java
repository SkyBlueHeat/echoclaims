package dev.worldecho.domain.item;

import java.util.List;

/**
 * Result of scoring an item, including the factors that produced the value so an
 * administrator can understand why an item was selected.
 */
public record ItemScore(int value, List<ScoreFactor> factors) {

    public ItemScore {
        value = Math.max(0, value);
        factors = List.copyOf(factors);
    }

    public static ItemScore zero() {
        return new ItemScore(0, List.of());
    }

    public String explain() {
        StringBuilder builder = new StringBuilder();
        for (ScoreFactor factor : factors) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(factor.name()).append('=').append(factor.points());
        }
        return builder.toString();
    }

    public record ScoreFactor(String name, int points) {
    }
}
