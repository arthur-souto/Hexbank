package com.hexbank.shared.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Especificação: docs/architecture/ARCHITECTURE.md, seção "AuditEntry e porta de auditoria" (BR-050, BR-051, ADR-0004). */
class AuditEntryTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-10T12:00:00Z");
    private static final UUID PRINCIPAL_ID = UUID.randomUUID();
    private static final UUID AGGREGATE_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    private static AuditEntry withReason(String reason) {
        return new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED, AuditAggregateType.ACCOUNT,
                AGGREGATE_ID, REQUEST_ID, null, null, null, reason);
    }

    private static AuditEntry withStatus(String fromStatus, String toStatus) {
        return new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED, AuditAggregateType.ACCOUNT,
                AGGREGATE_ID, REQUEST_ID, null, fromStatus, toStatus, null);
    }

    /** SHR-AU-01 — Registro válido. */
    @Nested
    class RegistroValido {

        @Test
        void aceitaTodosOsObrigatorios() {
            AuditEntry entry = new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.FUNDING_POSTED,
                    AuditAggregateType.JOURNAL, AGGREGATE_ID, REQUEST_ID, null, null, null, null);

            assertThat(entry.id()).isEqualTo(ID);
            assertThat(entry.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(entry.principalId()).isEqualTo(PRINCIPAL_ID);
            assertThat(entry.action()).isEqualTo(AuditAction.FUNDING_POSTED);
            assertThat(entry.aggregateType()).isEqualTo(AuditAggregateType.JOURNAL);
            assertThat(entry.aggregateId()).isEqualTo(AGGREGATE_ID);
            assertThat(entry.requestId()).isEqualTo(REQUEST_ID);
            assertThat(entry.correlationId()).isNull();
            assertThat(entry.fromStatus()).isNull();
            assertThat(entry.toStatus()).isNull();
            assertThat(entry.reason()).isNull();
        }

        @Test
        void aceitaOsOpcionaisPreenchidos() {
            AuditEntry entry = new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED,
                    AuditAggregateType.ACCOUNT, AGGREGATE_ID, REQUEST_ID, "corr-1", "PENDING", "ACTIVE", "CUSTOMER_REQUEST");

            assertThat(entry.correlationId()).isEqualTo("corr-1");
            assertThat(entry.fromStatus()).isEqualTo("PENDING");
            assertThat(entry.toStatus()).isEqualTo("ACTIVE");
            assertThat(entry.reason()).isEqualTo("CUSTOMER_REQUEST");
        }

        @Test
        void recusaIdNulo() {
            assertThatThrownBy(() -> new AuditEntry(null, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED,
                    AuditAggregateType.ACCOUNT, AGGREGATE_ID, REQUEST_ID, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void recusaOccurredAtNulo() {
            assertThatThrownBy(() -> new AuditEntry(ID, null, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED,
                    AuditAggregateType.ACCOUNT, AGGREGATE_ID, REQUEST_ID, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void recusaPrincipalIdNulo() {
            assertThatThrownBy(() -> new AuditEntry(ID, OCCURRED_AT, null, AuditAction.ACCOUNT_CREATED,
                    AuditAggregateType.ACCOUNT, AGGREGATE_ID, REQUEST_ID, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void recusaActionNula() {
            assertThatThrownBy(() -> new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, null,
                    AuditAggregateType.ACCOUNT, AGGREGATE_ID, REQUEST_ID, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void recusaAggregateTypeNulo() {
            assertThatThrownBy(() -> new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED,
                    null, AGGREGATE_ID, REQUEST_ID, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void recusaAggregateIdNulo() {
            assertThatThrownBy(() -> new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED,
                    AuditAggregateType.ACCOUNT, null, REQUEST_ID, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void recusaRequestIdNulo() {
            assertThatThrownBy(() -> new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED,
                    AuditAggregateType.ACCOUNT, AGGREGATE_ID, null, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @EmptySource
        @ValueSource(strings = {" ", "\t\n"})
        void recusaRequestIdEmBranco(String requestId) {
            assertThatThrownBy(() -> new AuditEntry(ID, OCCURRED_AT, PRINCIPAL_ID, AuditAction.ACCOUNT_CREATED,
                    AuditAggregateType.ACCOUNT, AGGREGATE_ID, requestId, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** SHR-AU-02 — Sem dado sensível (#1 a #3; o #4 é de integração). */
    @Nested
    class SemDadoSensivel {

        @ParameterizedTest
        @ValueSource(strings = {"CUSTOMER_REQUEST", "FRAUD", "KYC_2", "A", "R2D2_C3PO"})
        void aceitaReasonEmUpperSnakeCase(String reason) {
            assertThat(withReason(reason).reason()).isEqualTo(reason);
        }

        @Test
        void aceitaReasonNoLimiteDeTamanho() {
            String reason = "A".repeat(64);

            assertThat(withReason(reason).reason()).isEqualTo(reason);
        }

        @ParameterizedTest
        @EmptySource
        @ValueSource(strings = {
                "cliente pediu",        // texto livre
                "customer_request",     // minúsculas
                "Customer_Request",     // misto
                "CUSTOMER REQUEST",     // espaço
                "CUSTOMER-REQUEST",     // hífen
                "1_CUSTOMER",           // começa com dígito
                "_CUSTOMER",            // começa com underscore
                "CUSTOMER_",            // termina com underscore
                "CUSTOMER__REQUEST",    // underscore duplo
                "CUSTOMER_REQUEST\n",   // quebra de linha no fim
                "ÇÃO"                   // fora de A-Z
        })
        void recusaReasonForaDoFormato(String reason) {
            assertThatThrownBy(() -> withReason(reason)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void recusaReasonAcimaDoLimiteDeTamanho() {
            assertThatThrownBy(() -> withReason("A".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void mensagemDeErroNaoExpoeOReason() {
            String reason = "cliente pediu";

            assertThatThrownBy(() -> withReason(reason))
                    .isInstanceOf(IllegalArgumentException.class)
                    .message().doesNotContain(reason);
        }

        @Test
        void aceitaStatusAmbosNulos() {
            assertThatCode(() -> withStatus(null, null)).doesNotThrowAnyException();
        }

        @Test
        void aceitaStatusAmbosPreenchidos() {
            AuditEntry entry = withStatus("ACTIVE", "BLOCKED");

            assertThat(entry.fromStatus()).isEqualTo("ACTIVE");
            assertThat(entry.toStatus()).isEqualTo("BLOCKED");
        }

        @Test
        void recusaSoFromStatus() {
            assertThatThrownBy(() -> withStatus("ACTIVE", null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void recusaSoToStatus() {
            assertThatThrownBy(() -> withStatus(null, "BLOCKED")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
