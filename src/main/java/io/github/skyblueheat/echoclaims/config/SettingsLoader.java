package io.github.skyblueheat.echoclaims.config;

import io.github.skyblueheat.echoclaims.domain.item.ItemScoreWeights;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Validates untrusted configuration into {@link EchoClaimsSettings}.
 *
 * <p>Invalid values never abort startup: each one is replaced by a documented default and
 * reported as a warning.</p>
 */
public final class SettingsLoader {

    private static final ItemScoreWeights DEFAULT_WEIGHTS = ItemScoreWeights.defaults();

    private SettingsLoader() {
    }

    public static SettingsLoadResult load(ConfigurationSource source) {
        Objects.requireNonNull(source, "source");
        List<String> warnings = new ArrayList<>();

        EchoClaimsSettings settings = new EchoClaimsSettings(
                string(source, warnings, "locale", "en"),
                rawString(source, warnings, "message-prefix", "<gray>[<aqua>EchoClaims</aqua>]</gray> "),
                weights(source, warnings),
                string(source, warnings, "persistence.sqlite-file", "echoclaims.db"),
                Duration.ofSeconds(
                        integer(source, warnings, "persistence.shutdown-timeout-seconds", 10, 1, 300)
                ),
                integer(source, warnings, "persistence.write-queue-capacity", 2_000, 16, 100_000),
                integer(source, warnings, "persistence.write-batch-size", 64, 1, 1_000),
                bool(source, warnings, "debug", false)
        );

        return new SettingsLoadResult(settings, warnings);
    }

    private static ItemScoreWeights weights(ConfigurationSource source, List<String> warnings) {
        return new ItemScoreWeights(
                intMap(source, warnings, "scoring.material-scores", DEFAULT_WEIGHTS.materialScores()),
                intMap(source, warnings, "scoring.material-tiers", DEFAULT_WEIGHTS.materialTiers()),
                integer(source, warnings, "scoring.default-material-score",
                        DEFAULT_WEIGHTS.defaultMaterialScore(), 0, 10_000),
                integer(source, warnings, "scoring.enchantment-base",
                        DEFAULT_WEIGHTS.enchantmentBase(), 0, 10_000),
                integer(source, warnings, "scoring.enchantment-per-level",
                        DEFAULT_WEIGHTS.enchantmentPerLevel(), 0, 10_000),
                integer(source, warnings, "scoring.custom-name-bonus",
                        DEFAULT_WEIGHTS.customNameBonus(), 0, 10_000),
                integer(source, warnings, "scoring.unbreakable-bonus",
                        DEFAULT_WEIGHTS.unbreakableBonus(), 0, 10_000),
                integer(source, warnings, "scoring.provider-identified-bonus",
                        DEFAULT_WEIGHTS.providerIdentifiedBonus(), 0, 10_000),
                integer(source, warnings, "scoring.amount-bonus-cap",
                        DEFAULT_WEIGHTS.amountBonusCap(), 0, 10_000),
                integer(source, warnings, "scoring.wear-penalty-cap",
                        DEFAULT_WEIGHTS.wearPenaltyCap(), 0, 10_000)
        );
    }

    private static String string(
            ConfigurationSource source,
            List<String> warnings,
            String path,
            String fallback
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return fallback;
        }
        if (raw.get() instanceof String value && !value.isBlank()) {
            return value.strip();
        }
        warnings.add(invalid(path, raw.get(), fallback));
        return fallback;
    }

    private static String rawString(
            ConfigurationSource source,
            List<String> warnings,
            String path,
            String fallback
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return fallback;
        }
        if (raw.get() instanceof String value && !value.isBlank()) {
            return value;
        }
        warnings.add(invalid(path, raw.get(), fallback));
        return fallback;
    }

    private static boolean bool(
            ConfigurationSource source,
            List<String> warnings,
            String path,
            boolean fallback
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return fallback;
        }
        if (raw.get() instanceof Boolean value) {
            return value;
        }
        warnings.add(invalid(path, raw.get(), fallback));
        return fallback;
    }

    private static int integer(
            ConfigurationSource source,
            List<String> warnings,
            String path,
            int fallback,
            int minimum,
            int maximum
    ) {
        Optional<Object> raw = source.raw(path);
        if (raw.isEmpty()) {
            return fallback;
        }
        if (!(raw.get() instanceof Number number)) {
            warnings.add(invalid(path, raw.get(), fallback));
            return fallback;
        }

        int value = number.intValue();
        if (value < minimum || value > maximum) {
            int clamped = Math.min(maximum, Math.max(minimum, value));
            warnings.add(path + " must be between " + minimum + " and " + maximum
                    + "; using " + clamped);
            return clamped;
        }
        return value;
    }

    private static Map<String, Integer> intMap(
            ConfigurationSource source,
            List<String> warnings,
            String path,
            Map<String, Integer> fallback
    ) {
        Set<String> keys = source.childKeys(path);
        if (keys.isEmpty()) {
            return fallback;
        }

        Map<String, Integer> values = new LinkedHashMap<>();
        for (String key : keys) {
            Optional<Object> raw = source.raw(path + "." + key);
            if (raw.orElse(null) instanceof Number number) {
                values.put(key, number.intValue());
            } else {
                warnings.add(path + "." + key + " must be a number and was ignored");
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private static String invalid(String path, Object actual, Object fallback) {
        return path + " is invalid (" + describe(actual) + "); using " + fallback;
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
