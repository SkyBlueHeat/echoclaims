package io.github.skyblueheat.echoclaims.persistence.migration;

import java.util.List;
import java.util.Objects;

/**
 * One forward-only schema step. Statements must be safe to run against any database that
 * already contains data from earlier versions.
 */
public record Migration(int version, String description, List<String> statements) {

    public Migration {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(description, "description");
        statements = List.copyOf(statements);
    }
}
