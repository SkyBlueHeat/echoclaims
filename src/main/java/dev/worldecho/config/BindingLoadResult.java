package dev.worldecho.config;

import dev.worldecho.domain.binding.BindingDiagnostic;
import dev.worldecho.domain.binding.BindingRegistry;

import java.util.List;
import java.util.Objects;

/**
 * Result of loading and validating {@code bindings.yml}.
 *
 * <p>{@code fatalError} is {@code true} only when the entire file could not be parsed
 * (missing version, unsupported version, invalid root structure). A partially invalid
 * but parseable file sets {@code fatalError} to {@code false} and carries diagnostics
 * for the invalid entries while the valid entries populate the registry.</p>
 *
 * @param registry    the validated bindings, or an empty registry on fatal error
 * @param diagnostics every problem discovered during loading
 * @param fatalError  whether the entire file was rejected
 */
public record BindingLoadResult(
        BindingRegistry registry,
        List<BindingDiagnostic> diagnostics,
        boolean fatalError
) {

    public BindingLoadResult {
        Objects.requireNonNull(registry, "registry");
        diagnostics = List.copyOf(Objects.requireNonNullElse(diagnostics, List.of()));
    }

    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(d -> !d.isError());
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(BindingDiagnostic::isError);
    }
}
