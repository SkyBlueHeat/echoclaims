package dev.worldecho.application;

import dev.worldecho.config.BindingLoadResult;
import dev.worldecho.domain.binding.BindingRegistry;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure-Java coordinator that decides whether a newly loaded {@link BindingLoadResult}
 * should replace the currently active {@link BindingRegistry}.
 *
 * <p>Rules:
 * <ul>
 *   <li>On <b>startup</b> (no previous registry): a fatal error falls back to an empty
 *       registry; a non-fatal result always replaces.</li>
 *   <li>On <b>reload</b> (previous registry exists): a fatal error keeps the previous
 *       registry; a non-fatal result always replaces, even when it contains diagnostics
 *       for invalid entries.</li>
 * </ul>
 *
 * <p>This class is intentionally free of Bukkit, file I/O, and threading concerns so it
 * can be unit-tested in a pure JVM environment.</p>
 */
public final class BindingReloadCoordinator {

    private BindingReloadCoordinator() {
    }

    /**
     * Decides which registry to publish after a load attempt.
     *
     * @param previous the currently active registry, or {@code null} on startup
     * @param loaded   the load result to evaluate
     * @return the registry that should become active, plus a flag indicating whether
     *         the previous registry was kept
     */
    public static ReloadDecision decide(BindingRegistry previous, BindingLoadResult loaded) {
        Objects.requireNonNull(loaded, "loaded");

        if (loaded.fatalError() && previous != null) {
            return new ReloadDecision(previous, true, false);
        }

        return new ReloadDecision(loaded.registry(), false, true);
    }

    /**
     * Result of a reload decision.
     *
     * @param registry         the registry to publish
     * @param keptPrevious     true when the previous registry was kept due to a fatal error
     * @param replaced         true when the active registry was replaced
     */
    public record ReloadDecision(
            BindingRegistry registry,
            boolean keptPrevious,
            boolean replaced
    ) {
        public ReloadDecision {
            Objects.requireNonNull(registry, "registry");
        }

        public Optional<BindingRegistry> previousKept() {
            return keptPrevious ? Optional.of(registry) : Optional.empty();
        }
    }
}
