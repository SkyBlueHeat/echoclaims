package dev.worldecho.domain.scenario;

import dev.worldecho.domain.content.Capability;
import dev.worldecho.domain.content.SemanticRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure-Java formatter that converts eligibility results and catalog entries into
 * human-readable lines suitable for console or in-game display.
 *
 * <p>This class is intentionally free of Bukkit dependencies so it can be unit-tested
 * in a pure JVM environment.</p>
 */
public final class EligibilityFormatter {

    private EligibilityFormatter() {
    }

    /**
     * Formats a profile for the {@code eligibility profiles} command.
     *
     * @return a list of formatted lines: one header + one line per profile
     */
    public static List<String> formatProfiles(EligibilityCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        List<String> lines = new ArrayList<>();
        for (EligibilityProfile profile : catalog.allProfiles()) {
            lines.add(formatProfile(profile));
        }
        return lines;
    }

    /**
     * Formats a single profile as a concise summary line.
     */
    public static String formatProfile(EligibilityProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(profile.id());
        sb.append(" [").append(profile.bindingType().name().toLowerCase(Locale.ROOT)).append("]");
        sb.append(" — ").append(profile.description());

        List<String> reqs = new ArrayList<>();
        for (SemanticRole role : new java.util.TreeSet<>(profile.requiredRoles())) {
            reqs.add("role:" + role.name());
        }
        for (Capability cap : new java.util.TreeSet<>(profile.requiredCapabilities())) {
            reqs.add("cap:" + cap.name());
        }
        if (profile.requiresFaction()) reqs.add("faction");
        if (profile.requiresRank()) reqs.add("rank");
        if (profile.requiresSuperior()) reqs.add("superior");
        for (String tag : new java.util.TreeSet<>(profile.requiredTags())) {
            reqs.add("tag:" + tag);
        }

        if (!reqs.isEmpty()) {
            sb.append(" | requires: ").append(String.join(", ", reqs));
        }
        return sb.toString();
    }

    /**
     * Formats a single eligibility result for the {@code eligibility check} command.
     *
     * @return a list of formatted lines
     */
    public static List<String> formatResult(EligibilityResult result) {
        Objects.requireNonNull(result, "result");
        List<String> lines = new ArrayList<>();
        lines.add("content: " + (result.contentKey() != null ? result.contentKey().toString() : "-"));
        lines.add("profile: " + result.profileId());
        lines.add("result: " + (result.eligible() ? "eligible" : "not eligible"));

        if (!result.eligible()) {
            for (SemanticRole role : new java.util.TreeSet<>(result.missingRoles())) {
                lines.add("Missing role: " + role.name());
            }
            for (Capability cap : new java.util.TreeSet<>(result.missingCapabilities())) {
                lines.add("Missing capability: " + cap.name());
            }
            for (String meta : result.missingMetadata()) {
                lines.add("Missing metadata: " + meta);
            }
            for (String tag : new java.util.TreeSet<>(result.missingTags())) {
                lines.add("Missing tag: " + tag);
            }
        }
        return lines;
    }

    /**
     * Formats a concise one-line summary for the {@code eligibility all} command.
     */
    public static String formatSummary(EligibilityResult result) {
        if (result.eligible()) {
            return result.profileId() + ": eligible";
        }
        List<String> missing = new ArrayList<>();
        for (Capability cap : new java.util.TreeSet<>(result.missingCapabilities())) {
            missing.add(kebabCase(cap.name()));
        }
        for (SemanticRole role : new java.util.TreeSet<>(result.missingRoles())) {
            missing.add(kebabCase(role.name()));
        }
        missing.addAll(result.missingMetadata());
        for (String tag : new java.util.TreeSet<>(result.missingTags())) {
            missing.add("tag:" + tag);
        }
        return result.profileId() + ": missing " + String.join(", ", missing);
    }

    private static String kebabCase(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
