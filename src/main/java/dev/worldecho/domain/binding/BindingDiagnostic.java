package dev.worldecho.domain.binding;

import java.util.Objects;

/**
 * A single validation problem discovered while loading bindings.
 *
 * <p>Diagnostics are informational: they never prevent the plugin from starting.
 * The caller decides whether to log them, show them in commands, or ignore them.</p>
 */
public record BindingDiagnostic(
        Severity severity,
        String path,
        String message
) {

    public enum Severity {
        WARNING,
        ERROR
    }

    public BindingDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return path + ": " + severity.name().toLowerCase() + " — " + message;
    }
}
