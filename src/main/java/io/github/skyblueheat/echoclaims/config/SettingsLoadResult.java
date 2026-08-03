package io.github.skyblueheat.echoclaims.config;

import java.util.List;
import java.util.Objects;

/**
 * Validated settings plus every problem that was corrected with a safe default.
 */
public record SettingsLoadResult(EchoClaimsSettings settings, List<String> warnings) {

    public SettingsLoadResult {
        Objects.requireNonNull(settings, "settings");
        warnings = List.copyOf(warnings);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
