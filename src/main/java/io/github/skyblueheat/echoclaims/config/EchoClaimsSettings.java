package io.github.skyblueheat.echoclaims.config;

import io.github.skyblueheat.echoclaims.domain.item.ItemScoreWeights;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public record EchoClaimsSettings(
        String locale,
        String messagePrefix,
        ItemScoreWeights itemScoreWeights,
        String sqliteFile,
        Duration shutdownTimeout,
        int writeQueueCapacity,
        int writeBatchSize,
        boolean debugLogging,
        boolean capturePlayerDeath,
        boolean capturePostRespawn,
        int maxRecentResults,
        int maxItemPayloadBytes,
        int evidenceQueueCapacity,
        boolean claimsEnabled,
        Duration claimRateLimitCooldown,
        int claimMaxDescriptionLength,
        Duration claimSelectionSessionTtl,
        int claimSelectionSessionMaxPlayers
) {

    public EchoClaimsSettings {
        locale = normalizeLocale(locale);
        messagePrefix = Objects.requireNonNullElse(messagePrefix, "");
        itemScoreWeights = Objects.requireNonNullElseGet(
                itemScoreWeights,
                ItemScoreWeights::defaults
        );
        sqliteFile = validateSqliteFile(sqliteFile);
        shutdownTimeout = shutdownTimeout == null
                ? Duration.ofSeconds(10)
                : shutdownTimeout;
        writeQueueCapacity = Math.max(16, writeQueueCapacity);
        writeBatchSize = Math.max(1, writeBatchSize);
        maxRecentResults = Math.max(1, Math.min(maxRecentResults, 100));
        maxItemPayloadBytes = Math.max(256, Math.min(maxItemPayloadBytes, 1_048_576));
        evidenceQueueCapacity = Math.max(16, evidenceQueueCapacity);
        claimRateLimitCooldown = claimRateLimitCooldown == null
                ? Duration.ofSeconds(30)
                : claimRateLimitCooldown;
        claimMaxDescriptionLength = Math.max(0, Math.min(claimMaxDescriptionLength, 1_000));
        claimSelectionSessionTtl = claimSelectionSessionTtl == null
                ? Duration.ofSeconds(120)
                : claimSelectionSessionTtl;
        claimSelectionSessionMaxPlayers = Math.max(1, Math.min(claimSelectionSessionMaxPlayers, 1_000));
    }

    private static String validateSqliteFile(String value) {
        String fallback = "echoclaims.db";
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if (!value.equals(value.strip())) {
            return fallback;
        }
        if (value.length() > 255) {
            return fallback;
        }
        if (value.equals(".") || value.equals("..") || value.contains("..")) {
            return fallback;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '\0') {
                return fallback;
            }
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-')) {
                return fallback;
            }
        }
        return value;
    }

    public static EchoClaimsSettings defaults() {
        return SettingsLoader.load(MapConfigurationSource.empty()).settings();
    }

    private static String normalizeLocale(String locale) {
        if (locale == null) {
            return "en";
        }
        return locale.strip().toLowerCase(Locale.ROOT).startsWith("tr") ? "tr" : "en";
    }
}
