package com.hexbank.shared.adapter.out.persistence;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.shared.adapter.out.persistence.jpa.AuditLogJpaRepository;
import com.hexbank.shared.domain.AuditAction;
import com.hexbank.shared.domain.AuditAggregateType;
import com.hexbank.shared.domain.AuditEntry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V4__audit.sql + AuditLogPersistenceAdapter. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md, seção "AuditEntry e porta de auditoria" (SHR-AU-03 #3).
 * SHR-AU-02 #5 e SHR-AU-03 #1/#2 dependem dos casos de uso e entram com eles.
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AuditLogPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuditLogPersistenceAdapter adapter;

    @Autowired
    AuditLogJpaRepository jpaRepository;

    private static AuditEntry entry(String fromStatus, String toStatus, String reason) {
        return new AuditEntry(UUID.randomUUID(), NOW, UUID.randomUUID(), AuditAction.ACCOUNT_CREATED,
                AuditAggregateType.ACCOUNT, UUID.randomUUID(), "req-123", "corr-1", fromStatus, toStatus, reason);
    }

    private UUID recorded() {
        AuditEntry entry = entry(null, null, null);
        adapter.record(entry);
        jpaRepository.flush();
        return entry.id();
    }

    private void insert(String action, String requestId, String fromStatus, String toStatus, String reason) {
        jdbc.update("""
                INSERT INTO audit.audit_log (id, occurred_at, principal_id, action, aggregate_type, aggregate_id,
                                             request_id, correlation_id, from_status, to_status, reason)
                VALUES (?, ?, ?, ?, 'ACCOUNT', ?, ?, NULL, ?, ?, ?)""",
                UUID.randomUUID(), Timestamp.from(NOW), UUID.randomUUID(), action, UUID.randomUUID(),
                requestId, fromStatus, toStatus, reason);
    }

    @Nested
    class Adapter {

        @Test
        void gravaTodosOsCampos() {
            AuditEntry entry = entry("ACTIVE", "BLOCKED", "CUSTOMER_REQUEST");

            adapter.record(entry);
            jpaRepository.flush();

            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM audit.audit_log WHERE id = ?", entry.id());
            assertThat(row)
                    .containsEntry("principal_id", entry.principalId())
                    .containsEntry("action", "ACCOUNT_CREATED")
                    .containsEntry("aggregate_type", "ACCOUNT")
                    .containsEntry("aggregate_id", entry.aggregateId())
                    .containsEntry("request_id", "req-123")
                    .containsEntry("correlation_id", "corr-1")
                    .containsEntry("from_status", "ACTIVE")
                    .containsEntry("to_status", "BLOCKED")
                    .containsEntry("reason", "CUSTOMER_REQUEST");
            assertThat(((Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(NOW);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void recordSemTransacaoERecusado() {
            assertThatThrownBy(() -> adapter.record(entry(null, null, null)))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }

    /** Defesa em profundidade: o banco repete as regras da AuditEntry (SHR-AU-01 e 02). */
    @Nested
    class RestricoesDaMigration {

        @Test
        void aceitaRegistroValido() {
            insert("FUNDING_POSTED", "req-1", null, null, "CUSTOMER_REQUEST");

            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_log", Long.class)).isEqualTo(1);
        }

        @Test
        void recusaAcaoDesconhecida() {
            assertThatThrownBy(() -> insert("ACCOUNT_DELETED", "req-1", null, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void recusaRequestIdEmBranco() {
            assertThatThrownBy(() -> insert("ACCOUNT_CREATED", "   ", null, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void recusaReasonEmTextoLivre() {
            assertThatThrownBy(() -> insert("ACCOUNT_CREATED", "req-1", null, null, "cliente pediu"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void recusaReasonComUnderscoreDuplicado() {
            assertThatThrownBy(() -> insert("ACCOUNT_CREATED", "req-1", null, null, "CUSTOMER__REQUEST"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void recusaSoFromStatus() {
            assertThatThrownBy(() -> insert("ACCOUNT_CREATED", "req-1", "ACTIVE", null, null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("status_pair");
        }
    }

    /** SHR-AU-03 #3 — registro gravado nunca muda. */
    @Nested
    class AppendOnly {

        @Test
        void recusaUpdate() {
            UUID id = recorded();

            assertThatThrownBy(() -> jdbc.update("UPDATE audit.audit_log SET reason = 'X' WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }

        @Test
        void recusaDelete() {
            UUID id = recorded();

            assertThatThrownBy(() -> jdbc.update("DELETE FROM audit.audit_log WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }

        @Test
        void recusaTruncate() {
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit.audit_log"))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }
    }
}
