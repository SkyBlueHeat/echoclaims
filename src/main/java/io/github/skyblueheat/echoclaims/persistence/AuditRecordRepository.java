package io.github.skyblueheat.echoclaims.persistence;

import io.github.skyblueheat.echoclaims.domain.audit.AuditRecord;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Repository for {@link AuditRecord} persistence.
 *
 * <p>Implementations are called exclusively from the EchoClaims writer thread, never
 * from the server main thread.</p>
 */
public interface AuditRecordRepository {

    void insert(AuditRecord record) throws SQLException;

    int insertAll(Collection<AuditRecord> records) throws SQLException;

    long count() throws SQLException;
}
