package io.github.skyblueheat.echoclaims.config;

import io.github.skyblueheat.echoclaims.domain.item.ItemScoreWeights;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record EchoClaimsSettings(
        String locale,
        String messagePrefix,
        ItemScoreWeights itemScoreWeights,
        String sqliteFile,
        Duration shutdownTimeout,
        int writeQueueCapacity,
        int writeBatchSize,
        boolean debugLogging,
        int retentionDays
) {

    public EchoClaimsSettings {
        locale = normalizeLocale(locale);
        messagePrefix = Objects.requireNonNullElse(messagePrefix, "");
        itemScoreWeights = Objects.requireNonNullElseGet(
                itemScoreWeights,
                ItemScoreWeights::defaults
        );
        sqliteFile = sqliteFile == null || sqliteFile.isBlank()
                ? "echoclaims.db"
                : sqliteFile.strip();
        shutdownTimeout = shutdownTimeout == null
                ? Duration.ofSeconds(10)
                : shutdownTimeout;
        writeQueueCapacity = Math.max(16, writeQueueCapacity);
        writeBatchSize = Math.max(1, writeBatchSize);
        retentionDays = Math.max(0, retentionDays);
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
