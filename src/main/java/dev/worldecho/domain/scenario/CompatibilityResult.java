package dev.worldecho.domain.scenario;

import java.util.List;

public record CompatibilityResult(boolean compatible, List<String> reasons) {

    public CompatibilityResult {
        reasons = List.copyOf(reasons);
    }

    public static CompatibilityResult success() {
        return new CompatibilityResult(true, List.of());
    }

    public static CompatibilityResult incompatible(List<String> reasons) {
        return new CompatibilityResult(false, reasons);
    }
}
