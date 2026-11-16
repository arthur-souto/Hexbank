package com.hexbank.accounts.application.usecase;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.accounts.application.port.in.OpenAccountUseCase;
import com.hexbank.accounts.application.port.in.command.OpenAccountCommand;
import com.hexbank.accounts.domain.Account;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.shared.application.port.out.AuditLogPort;
import com.hexbank.shared.domain.InstanceCurrency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * ACC-UC-01 — Abrir conta, com Postgres. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md#acc-uc-01--abrir-conta-post-v1accounts.
 * <p>
 * Sem @Transactional no teste (ver "Testes de rollback e dados append-only"): a transação é a do
 * próprio caso de uso, então commit e rollback são reais. Principal e requestId aleatórios por teste
 * e conferência por id, porque nada é limpo entre os testes (accounts, ledger e audit são append-only
 * ou acumulam).
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
class OpenAccountIntegrationTest {

    @Autowired OpenAccountUseCase openAccount;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    /** Spies: chamam o bean real; só os testes de falha mudam o comportamento. */
    @MockitoSpyBean OpenCustomerLedgerAccountUseCase openCustomerLedgerAccount;
    @MockitoSpyBean AuditLogPort auditLogPort;

    private static OpenAccountCommand commandFor(PrincipalId owner, String requestId) {
        return new OpenAccountCommand(owner, InstanceCurrency.BRL, requestId);
    }

    private static PrincipalId randomPrincipal() {
        return new PrincipalId(UUID.randomUUID());
    }

    private static String randomRequestId() {
        return "req-" + UUID.randomUUID();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long accountsOf(PrincipalId owner) {
        return count("SELECT count(*) FROM accounts.accounts WHERE owner_id = ?", owner.value());
    }

    private long ledgerAccountsOfOwner(PrincipalId owner) {
        return count("""
                SELECT count(*) FROM ledger.ledger_accounts la
                JOIN accounts.accounts a ON a.id = la.account_id
                WHERE a.owner_id = ?""", owner.value());
    }

    private long auditsFor(String requestId) {
        return count("SELECT count(*) FROM audit.audit_log WHERE request_id = ?", requestId);
    }

    /**
     * #2 — na mesma transação: 1 Account ACTIVE, 1 LedgerAccount LIABILITY BRL com o id da conta,
     * 1 projeção zerada e 1 auditoria ACCOUNT_CREATED (SHR-AU-03 #1).
     */
    @Test
    void acc_uc_01_2_grava_conta_conta_contabil_projecao_e_auditoria() {
        PrincipalId owner = randomPrincipal();
        String requestId = randomRequestId();

        Account account = openAccount.open(commandFor(owner, requestId));
        UUID accountId = account.getId().value();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT owner_id, currency, status, created_at FROM accounts.accounts WHERE id = ?", accountId);
        assertThat(row.get("owner_id")).isEqualTo(owner.value());
        assertThat(row.get("currency")).isEqualTo("BRL");
        assertThat(row.get("status")).isEqualTo("ACTIVE");

        Map<String, Object> ledger = jdbc.queryForMap(
                "SELECT id, type, currency FROM ledger.ledger_accounts WHERE account_id = ?", accountId);
        assertThat(ledger.get("type")).isEqualTo("LIABILITY");
        assertThat(ledger.get("currency")).isEqualTo("BRL");

        Map<String, Object> projection = jdbc.queryForMap(
                "SELECT booked_minor_units, reserved_minor_units FROM ledger.balance_projections WHERE ledger_account_id = ?",
                ledger.get("id"));
        assertThat(projection.get("booked_minor_units")).isEqualTo(0L);
        assertThat(projection.get("reserved_minor_units")).isEqualTo(0L);

        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT principal_id, action, aggregate_type, aggregate_id, occurred_at FROM audit.audit_log WHERE request_id = ?",
                requestId);
        assertThat(audit.get("principal_id")).isEqualTo(owner.value());
        assertThat(audit.get("action")).isEqualTo("ACCOUNT_CREATED");
        assertThat(audit.get("aggregate_type")).isEqualTo("ACCOUNT");
        assertThat(audit.get("aggregate_id")).isEqualTo(accountId);
        assertThat(audit.get("occurred_at")).isEqualTo(row.get("created_at"));
    }

    /** #5 — o mesmo dono abre duas contas: dois ids, duas contas contábeis, duas auditorias. */
    @Test
    void acc_uc_01_5_mesmo_dono_pode_abrir_varias_contas() {
        PrincipalId owner = randomPrincipal();

        Account first = openAccount.open(commandFor(owner, randomRequestId()));
        Account second = openAccount.open(commandFor(owner, randomRequestId()));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(accountsOf(owner)).isEqualTo(2);
        assertThat(ledgerAccountsOfOwner(owner)).isEqualTo(2);
    }

    /**
     * #6 — o ledger falha depois de a Account ter sido salva: rollback total, nem Account,
     * nem LedgerAccount, nem auditoria.
     */
    @Test
    void acc_uc_01_6_falha_no_ledger_desfaz_tudo() {
        PrincipalId owner = randomPrincipal();
        String requestId = randomRequestId();
        // O spy fica atrás do proxy transacional: o stubbing roda numa transação, como no PostJournalIntegrationTest.
        tx.executeWithoutResult(status ->
                doThrow(new IllegalStateException("falha simulada"))
                        .when(openCustomerLedgerAccount).open(any(), any()));

        assertThatThrownBy(() -> openAccount.open(commandFor(owner, requestId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha simulada");

        assertThat(accountsOf(owner)).isZero();
        assertThat(ledgerAccountsOfOwner(owner)).isZero();
        assertThat(auditsFor(requestId)).isZero();
    }

    /**
     * SHR-AU-03 #2 — a auditoria é gravada de verdade e só depois a operação falha: o registro
     * também é desfeito, junto com a conta e a conta contábil.
     */
    @Test
    void shr_au_03_2_falha_depois_de_auditar_desfaz_a_auditoria() {
        PrincipalId owner = randomPrincipal();
        String requestId = randomRequestId();
        tx.executeWithoutResult(status ->
                doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new IllegalStateException("falha depois de auditar");
                }).when(auditLogPort).record(any()));

        assertThatThrownBy(() -> openAccount.open(commandFor(owner, requestId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha depois de auditar");

        assertThat(auditsFor(requestId)).isZero();
        assertThat(accountsOf(owner)).isZero();
        assertThat(ledgerAccountsOfOwner(owner)).isZero();
    }
}
