package dev.worldecho.config;

import dev.worldecho.domain.item.ItemScoreWeights;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record WorldEchoSettings(
        String locale,
        boolean playerDeathCaptureEnabled,
        boolean recordDeathWithoutValuableItem,
        int minimumItemScore,
        boolean redactCustomItemNames,
        Set<String> ignoredWorlds,
        ItemScoreWeights itemScoreWeights,
        String sqliteFile,
        Duration shutdownTimeout,
        int writeQueueCapacity,
        int writeBatchSize,
        int recentDefaultCount,
        int recentMaximumCount,
        boolean itemIdentityEnabled,
        int itemHistoryDefaultLimit,
        int itemHistoryMaximumLimit,
        boolean automaticTrackingEnabled,
        boolean automaticTrackingPlayerInventories,
        boolean automaticTrackingReconcileOnJoin,
        boolean automaticTrackingReconcileOnRespawn,
        boolean automaticTrackingReconcileAfterInventoryEvents,
        boolean automaticTrackingTransformIdentityContinuity,
        boolean automaticTrackingDebugMessages
) {

    public WorldEchoSettings {
        locale = normalizeLocale(locale);
        minimumItemScore = Math.max(0, minimumItemScore);
        ignoredWorlds = Set.copyOf(Objects.requireNonNullElse(ignoredWorlds, Set.of()));
        itemScoreWeights = Objects.requireNonNullElseGet(
                itemScoreWeights,
                ItemScoreWeights::defaults
        );
        sqliteFile = sqliteFile == null || sqliteFile.isBlank()
                ? "worldecho.db"
                : sqliteFile.strip();
        shutdownTimeout = shutdownTimeout == null
                ? Duration.ofSeconds(10)
                : shutdownTimeout;
        writeQueueCapacity = Math.max(16, writeQueueCapacity);
        writeBatchSize = Math.max(1, writeBatchSize);
        recentDefaultCount = Math.max(1, recentDefaultCount);
        recentMaximumCount = Math.max(recentDefaultCount, recentMaximumCount);
        itemHistoryDefaultLimit = Math.max(1, itemHistoryDefaultLimit);
        itemHistoryMaximumLimit = Math.max(itemHistoryDefaultLimit, itemHistoryMaximumLimit);
    }

    public static WorldEchoSettings defaults() {
        return SettingsLoader.load(MapConfigurationSource.empty()).settings();
    }

    public boolean isWorldIgnored(String worldName) {
        if (worldName == null) {
            return false;
        }
        return ignoredWorlds.stream().anyMatch(ignored -> ignored.equalsIgnoreCase(worldName));
    }

    private static String normalizeLocale(String locale) {
        if (locale == null) {
            return "en";
        }
        return locale.strip().toLowerCase(Locale.ROOT).startsWith("tr") ? "tr" : "en";
    }
}
