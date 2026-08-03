package io.github.skyblueheat.echoclaims.domain.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit record persisted by the asynchronous write queue.
 *
 * <p>Audit records form the evidence chain that every generated consequence must be
 * explainable from. They are created on the server thread and persisted
 * asynchronously.</p>
 */
public record AuditRecord(
        UUID id,
        String category,
        String source,
        String details,
        long recordedAt
) {

    public AuditRecord {
        Objects.requireNonNull(id, "id");
        category = Objects.requireNonNull(category, "category").strip();
        if (category.isEmpty()) {
            throw new IllegalArgumentException("category cannot be blank");
        }
        source = Objects.requireNonNullElse(source, "").strip();
        details = Objects.requireNonNullElse(details, "").strip();
    }

    public static AuditRecord now(String category, String source, String details) {
        return new AuditRecord(UUID.randomUUID(), category, source, details, System.currentTimeMillis());
    }
}
