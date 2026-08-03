package dev.worldecho.domain.item;

import java.util.List;

/**
 * Immutable result of classifying an observed item's identity mode.
 *
 * @param mode           the {@link IdentityMode} (UNIQUE or LOT)
 * @param reasons        ordered list of human-readable classification reasons
 * @param confidence     confidence score in {@code [0, 1]} where 1 means certain
 */
public record IdentityClassificationResult(
        IdentityMode mode,
        List<String> reasons,
        double confidence
) {

    public IdentityClassificationResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public boolean isUnique() {
        return mode == IdentityMode.UNIQUE;
    }

    public boolean isLot() {
        return mode == IdentityMode.LOT;
    }

    public static IdentityClassificationResult unique(List<String> reasons, double confidence) {
        return new IdentityClassificationResult(IdentityMode.UNIQUE, reasons, confidence);
    }

    public static IdentityClassificationResult lot(List<String> reasons, double confidence) {
        return new IdentityClassificationResult(IdentityMode.LOT, reasons, confidence);
    }
}
