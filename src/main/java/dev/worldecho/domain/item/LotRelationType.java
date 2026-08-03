package dev.worldecho.domain.item;

import java.util.Locale;

/**
 * Type of relationship between two tracked item lots.
 *
 * <ul>
 *   <li>{@link #SPLIT_FROM} — this lot was split from a parent lot</li>
 *   <li>{@link #MERGED_INTO} — this lot was merged into a surviving lot</li>
 * </ul>
 */
public enum LotRelationType {
    SPLIT_FROM,
    MERGED_INTO;

    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LotRelationType fromToken(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (LotRelationType type : values()) {
            if (type.token().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown lot relation type: " + token);
    }
}
