package com.hexbank.ledger.application.usecase;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.ledger.application.port.in.FindInstitutionalAccountQuery;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.ledger.application.port.in.PostJournalUseCase;
import com.hexbank.ledger.application.port.in.command.EntryCommand;
import com.hexbank.ledger.application.port.in.command.PostJournalCommand;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.domain.EntrySide;
import com.hexbank.ledger.domain.JournalId;
import com.hexbank.ledger.domain.JournalReferenceType;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.ledger.domain.exceptions.InsufficientFundsException;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * LED-UC-02 — Lançar journal. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md#led-uc-02--lançar-journal.
 * <p>
 * Sem @Transactional no teste (ver "Testes de rollback e dados append-only"): cada post roda num
 * TransactionTemplate, como faria o caso de uso que chama o ledger, então commit e rollback são reais.
 * Ids aleatórios e conferência por id. O SIMULATED_CASH acumula entre testes: só se confere a variação.
 * <p>
 * Fora daqui: #5 (só existe BRL; o banco ainda garante pela FK fk_journal_entries_account_currency) e
 * #8 (ordem do lock, coberta no LedgerPersistenceIntegrationTest).
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
class PostJournalIntegrationTest {

    @Autowired PostJournalUseCase postJournal;
    @Autowired OpenCustomerLedgerAccountUseCase openCustomer;
    @Autowired FindInstitutionalAccountQuery institutional;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    /** Spy: chama o adapter real; só o teste de falha após a gravação do journal muda o comportamento. */
    @MockitoSpyBean BalanceProjectionRepository projections;

    LedgerAccountId cash;
    LedgerAccountId customer;

    @BeforeEach
    void setUp() {
        cash = institutional.byCode("SIMULATED_CASH:BRL");
        customer = tx.execute(status -> openCustomer.open(UUID.randomUUID(), InstanceCurrency.BRL));
    }

    // ---------- helpers ----------

    private static EntryCommand debit(LedgerAccountId id, long minorUnits) {
        return new EntryCommand(id, EntrySide.DEBIT, new Money(InstanceCurrency.BRL, minorUnits));
    }

    private static EntryCommand credit(LedgerAccountId id, long minorUnits) {
        return new EntryCommand(id, EntrySide.CREDIT, new Money(InstanceCurrency.BRL, minorUnits));
    }

    private static PostJournalCommand funding(UUID referenceId, EntryCommand... entries) {
        return new PostJournalCommand(JournalReferenceType.FUNDING, referenceId, List.of(entries));
    }

    private JournalId postInTransaction(PostJournalCommand command) {
        return tx.execute(status -> postJournal.post(command));
    }

    /** Coloca saldo no cliente com um funding commitado. */
    private void fundCustomer(long minorUnits) {
        postInTransaction(funding(UUID.randomUUID(), debit(cash, minorUnits), credit(customer, minorUnits)));
    }

    private long booked(LedgerAccountId id) {
        return jdbc.queryForObject(
                "SELECT booked_minor_units FROM ledger.balance_projections WHERE ledger_account_id = ?", Long.class, id.value());
    }

    private long version(LedgerAccountId id) {
        return jdbc.queryForObject(
                "SELECT version FROM ledger.balance_projections WHERE ledger_account_id = ?", Long.class, id.value());
    }

    private long journalsFor(UUID referenceId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM ledger.journals WHERE reference_type = 'FUNDING' AND reference_id = ?", Long.class, referenceId);
    }

    private long entriesFor(LedgerAccountId id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM ledger.journal_entries WHERE ledger_account_id = ?", Long.class, id.value());
    }

    // ---------- cenários ----------

    /** #1 — grava 1 journal + 2 entries e move os dois saldos. */
    @Test
    void led_uc_02_1_funding_grava_journal_e_atualiza_saldos() {
        long cashBefore = booked(cash);
        UUID referenceId = UUID.randomUUID();

        JournalId journalId = postInTransaction(funding(referenceId, debit(cash, 10_000), credit(customer, 10_000)));

        assertThat(journalId).isNotNull();
        assertThat(jdbc.queryForObject("SELECT reference_id FROM ledger.journals WHERE id = ?", UUID.class, journalId.value()))
                .isEqualTo(referenceId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger.journal_entries WHERE journal_id = ?", Long.class, journalId.value()))
                .isEqualTo(2);
        assertThat(booked(customer)).isEqualTo(10_000);
        assertThat(booked(cash) - cashBefore).isEqualTo(10_000);
        assertThat(version(customer)).isEqualTo(1);
    }

    /** #2 — a projeção do cliente bate com o recálculo pelos entries (LED-BP-10). */
    @Test
    void led_uc_02_2_projecao_bate_com_recalculo_pelos_entries() {
        fundCustomer(10_000);
        fundCustomer(2_500);
        postInTransaction(funding(UUID.randomUUID(), debit(customer, 4_000), credit(cash, 4_000)));

        // Cliente é LIABILITY: crédito soma, débito subtrai.
        Long recalculated = jdbc.queryForObject("""
                SELECT SUM(CASE side WHEN 'CREDIT' THEN minor_units ELSE -minor_units END)
                  FROM ledger.journal_entries WHERE ledger_account_id = ?
                """, Long.class, customer.value());

        assertThat(recalculated).isEqualTo(8_500);
        assertThat(booked(customer)).isEqualTo(recalculated);
    }

    /** #3 — desbalanceado: IllegalArgumentException, nada gravado. */
    @Test
    void led_uc_02_3_desbalanceado_nao_grava_nada() {
        long cashBefore = booked(cash);
        UUID referenceId = UUID.randomUUID();

        assertThatThrownBy(() -> postInTransaction(funding(referenceId, debit(cash, 10_000), credit(customer, 9_000))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(journalsFor(referenceId)).isZero();
        assertThat(booked(customer)).isZero();
        assertThat(booked(cash)).isEqualTo(cashBefore);
    }

    /** #4 — LedgerAccountId inexistente: IllegalArgumentException, nada gravado. */
    @Test
    void led_uc_02_4_conta_inexistente_nao_grava_nada() {
        long cashBefore = booked(cash);
        UUID referenceId = UUID.randomUUID();
        LedgerAccountId ghost = new LedgerAccountId(UUID.randomUUID());

        assertThatThrownBy(() -> postInTransaction(funding(referenceId, debit(cash, 10_000), credit(ghost, 10_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ghost.toString());

        assertThat(journalsFor(referenceId)).isZero();
        assertThat(booked(cash)).isEqualTo(cashBefore);
    }

    /** #4b — conta existe mas sem projeção: IllegalStateException (dado inconsistente), nada gravado. */
    @Test
    void led_uc_02_4b_conta_sem_projecao_e_dado_inconsistente() {
        long cashBefore = booked(cash);
        UUID referenceId = UUID.randomUUID();
        // Só pelo banco dá para criar esse estado: o LED-UC-01 sempre cria a projeção junto.
        LedgerAccountId orphan = new LedgerAccountId(UUID.randomUUID());
        jdbc.update("INSERT INTO ledger.ledger_accounts (id, account_id, currency, type) VALUES (?, ?, 'BRL', 'LIABILITY')",
                orphan.value(), UUID.randomUUID());

        assertThatThrownBy(() -> postInTransaction(funding(referenceId, debit(cash, 10_000), credit(orphan, 10_000))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(orphan.toString());

        assertThat(journalsFor(referenceId)).isZero();
        assertThat(booked(cash)).isEqualTo(cashBefore);
    }

    /** #6 — débito que deixa o cliente negativo: InsufficientFundsException; nenhuma projeção muda. */
    @Test
    void led_uc_02_6_saldo_insuficiente_nao_grava_nada() {
        fundCustomer(5_000);
        long cashBefore = booked(cash);
        UUID referenceId = UUID.randomUUID();

        assertThatThrownBy(() -> postInTransaction(funding(referenceId, debit(customer, 10_000), credit(cash, 10_000))))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(journalsFor(referenceId)).isZero();
        assertThat(booked(customer)).isEqualTo(5_000);
        assertThat(booked(cash)).isEqualTo(cashBefore);
    }

    /** #6 (borda) — débito que zera o saldo do cliente é permitido. */
    @Test
    void led_uc_02_6_debito_que_zera_o_saldo_e_permitido() {
        fundCustomer(5_000);

        postInTransaction(funding(UUID.randomUUID(), debit(customer, 5_000), credit(cash, 5_000)));

        assertThat(booked(customer)).isZero();
    }

    /**
     * #7 — (FUNDING, f1) repetido: violação de UNIQUE e rollback. A segunda tentativa chega a atualizar
     * as projeções antes de falhar no flush/commit; o rollback desfaz isso também.
     */
    @Test
    void led_uc_02_7_referencia_repetida_desfaz_o_segundo_lancamento() {
        UUID referenceId = UUID.randomUUID();
        postInTransaction(funding(referenceId, debit(cash, 1_000), credit(customer, 1_000)));
        long cashBefore = booked(cash);

        // A violação pode surgir só no flush/commit: por isso a exceção é esperada do execute().
        assertThatThrownBy(() -> postInTransaction(funding(referenceId, debit(cash, 1_000), credit(customer, 1_000))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(journalsFor(referenceId)).isEqualTo(1);
        assertThat(entriesFor(customer)).isEqualTo(1);
        assertThat(booked(customer)).isEqualTo(1_000);
        assertThat(booked(cash)).isEqualTo(cashBefore);
        assertThat(version(customer)).isEqualTo(1);
    }

    /**
     * BR-012 — falha depois de o journal ser gravado (no update das projeções): o journal também
     * é desfeito. Nunca existe lançamento sem saldo.
     */
    @Test
    void falha_ao_gravar_projecao_desfaz_o_journal() {
        long cashBefore = booked(cash);
        UUID referenceId = UUID.randomUUID();
        // O spy fica atrás do proxy transacional do adapter (MANDATORY): até a chamada de stubbing
        // precisa de uma transação aberta, senão o proxy recusa e o stub fica pela metade.
        tx.executeWithoutResult(status ->
                doThrow(new IllegalStateException("falha simulada")).when(projections).update(any()));

        assertThatThrownBy(() -> postInTransaction(funding(referenceId, debit(cash, 10_000), credit(customer, 10_000))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha simulada");

        assertThat(journalsFor(referenceId)).isZero();
        assertThat(entriesFor(customer)).isZero();
        assertThat(booked(customer)).isZero();
        assertThat(booked(cash)).isEqualTo(cashBefore);
    }

    /**
     * Propagação REQUIRED: com transação de quem chama, participa dela (testes acima); sem nenhuma,
     * abre uma só para o post, que continua atômico.
     */
    @Test
    void sem_transacao_de_quem_chama_o_post_abre_a_sua() {
        UUID referenceId = UUID.randomUUID();

        postJournal.post(funding(referenceId, debit(cash, 100), credit(customer, 100)));

        assertThat(journalsFor(referenceId)).isEqualTo(1);
        assertThat(booked(customer)).isEqualTo(100);
    }
}
