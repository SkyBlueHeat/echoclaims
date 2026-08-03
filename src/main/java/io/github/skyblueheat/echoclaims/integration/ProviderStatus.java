package io.github.skyblueheat.echoclaims.integration;

public record ProviderStatus(
        String providerId,
        int priority,
        ProviderHealth health,
        boolean suppressed,
        long failures
) {

    public String describe() {
        String state = suppressed ? "SUPPRESSED" : health.name();
        return providerId + "=" + state + (failures > 0 ? "(" + failures + " errors)" : "");
    }
}
