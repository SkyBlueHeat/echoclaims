package io.github.skyblueheat.echoclaims.config;

import io.github.skyblueheat.echoclaims.domain.item.ItemScoreWeights;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.nio.file.Path;

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
        if (stripped.length() > 255) {
            return fallback;
        }
        String fileName = Path.of(stripped).getFileName().toString();
        if (!fileName.equals(stripped)) {
            return fallback;
        }
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")) {
            return fallback;
        }
        return fileName;
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
