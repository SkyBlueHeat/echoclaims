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
        boolean debugLogging
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
    }

    private static String validateSqliteFile(String value) {
        String fallback = "echoclaims.db";
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String stripped = value.strip();
        if (stripped.contains("/") || stripped.contains("\\") || stripped.contains("..")) {
            return fallback;
        }
        if (stripped.equals(".") || stripped.equals("-") || stripped.length() > 255) {
            return fallback;
        }
        return stripped;
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
