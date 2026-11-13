package com.hexbank.ledger.application.usecase;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.ledger.application.port.in.FindInstitutionalAccountQuery;
import com.hexbank.ledger.application.port.in.GetLedgerBalanceQuery;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.ledger.application.port.in.PostJournalUseCase;
import com.hexbank.ledger.application.port.in.command.EntryCommand;
import com.hexbank.ledger.application.port.in.command.PostJournalCommand;
import com.hexbank.ledger.application.port.in.view.LedgerBalanceView;
import com.hexbank.ledger.domain.EntrySide;
import com.hexbank.ledger.domain.JournalReferenceType;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LED-UC-03 — Consultar saldo. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md#led-uc-03--consultar-saldo.
 * <p>
 * Sem @Transactional no teste (ver "Testes de rollback e dados append-only"): abertura e funding são
 * commitados de verdade, e a consulta lê o que está no banco. Ids aleatórios e conferência por id.
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
class GetLedgerBalanceIntegrationTest {

    @Autowired GetLedgerBalanceQuery getLedgerBalance;
    @Autowired OpenCustomerLedgerAccountUseCase openCustomer;
    @Autowired PostJournalUseCase postJournal;
    @Autowired FindInstitutionalAccountQuery institutional;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private static Money brl(long minorUnits) {
        return new Money(InstanceCurrency.BRL, minorUnits);
    }

    private UUID openCustomerAccount() {
        UUID accountId = UUID.randomUUID();
        tx.executeWithoutResult(status -> openCustomer.open(accountId, InstanceCurrency.BRL));
        return accountId;
    }

    private void fund(UUID accountId, long minorUnits) {
        LedgerAccountId cash = institutional.byCode("SIMULATED_CASH:BRL");
        LedgerAccountId customer = jdbc.queryForObject(
                "SELECT id FROM ledger.ledger_accounts WHERE account_id = ?",
                (rs, n) -> new LedgerAccountId(rs.getObject("id", UUID.class)), accountId);
        tx.executeWithoutResult(status -> postJournal.post(new PostJournalCommand(
                JournalReferenceType.FUNDING, UUID.randomUUID(), List.of(
                        new EntryCommand(cash, EntrySide.DEBIT, brl(minorUnits)),
                        new EntryCommand(customer, EntrySide.CREDIT, brl(minorUnits))))));
    }

    /** #1 — depois de um funding de 10000: booked 10000, reserved 0, available 10000. */
    @Test
    void led_uc_03_1_saldo_depois_de_funding() {
        UUID accountId = openCustomerAccount();
        fund(accountId, 10_000);

        LedgerBalanceView view = getLedgerBalance.byAccountId(accountId).orElseThrow();

        assertThat(view.accountId()).isEqualTo(accountId);
        assertThat(view.booked()).isEqualTo(brl(10_000));
        assertThat(view.reserved()).isEqualTo(brl(0));
        assertThat(view.available()).isEqualTo(brl(10_000));
    }

    /** #1 — conta recém-aberta: tudo zerado (LED-BP-01). */
    @Test
    void led_uc_03_1_conta_recem_aberta_tem_saldo_zero() {
        UUID accountId = openCustomerAccount();

        LedgerBalanceView view = getLedgerBalance.byAccountId(accountId).orElseThrow();

        assertThat(view.booked()).isEqualTo(brl(0));
        assertThat(view.reserved()).isEqualTo(brl(0));
        assertThat(view.available()).isEqualTo(brl(0));
    }

    /** #2 — accountId sem conta contábil: Optional.empty() (o accounts transforma em 404). */
    @Test
    void led_uc_03_2_conta_inexistente_devolve_vazio() {
        assertThat(getLedgerBalance.byAccountId(UUID.randomUUID())).isEmpty();
    }

    /** #3 — asOf é o instante da leitura. */
    @Test
    void led_uc_03_3_as_of_e_o_instante_da_leitura() {
        UUID accountId = openCustomerAccount();
        Instant before = Instant.now();

        Instant asOf = getLedgerBalance.byAccountId(accountId).orElseThrow().asOf();

        assertThat(asOf).isBetween(before, Instant.now());
    }

    /** #4 — conta existe mas sem projeção (só criável pelo banco): IllegalStateException. */
    @Test
    void led_uc_03_4_conta_sem_projecao_e_dado_inconsistente() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("INSERT INTO ledger.ledger_accounts (id, account_id, currency, type) VALUES (?, ?, 'BRL', 'LIABILITY')",
                UUID.randomUUID(), accountId);

        assertThatThrownBy(() -> getLedgerBalance.byAccountId(accountId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(accountId.toString());
    }
}
