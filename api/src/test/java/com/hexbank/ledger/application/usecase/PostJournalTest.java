package com.hexbank.ledger.application.usecase;

import com.hexbank.ledger.application.port.in.command.EntryCommand;
import com.hexbank.ledger.application.port.in.command.PostJournalCommand;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.application.port.out.JournalRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.*;
import com.hexbank.ledger.domain.exceptions.InsufficientFundsException;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Especificação: docs/architecture/ARCHITECTURE.md, "LED-UC-02 — Lançar journal" (BR-010, BR-012, BR-013, BR-016).
 *
 * Teste unitário com as portas de saída mockadas: verifica a orquestração (o que é carregado, travado,
 * aplicado e gravado). "Nada gravado" aqui significa que save/update não foram chamados; o rollback real
 * no Postgres (#3 a #7) e a violação de UNIQUE (#7) ficam para o PostJournalIntegrationTest.
 *
 * Cenário #5 (moeda do entry ≠ moeda da conta) não é testável hoje: InstanceCurrency só tem BRL.
 */
@ExtendWith(MockitoExtension.class)
class PostJournalTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final LedgerAccountId CASH_ID = LedgerAccountId.of("00000000-0000-0000-0000-00000000ca5e");
    private static final LedgerAccountId CUSTOMER_ID = LedgerAccountId.of("3f1c9a2e-7b4d-4e8a-9c6f-1a2b3c4d5e6f");

    private static final LedgerAccount CASH =
            LedgerAccount.institutional(CASH_ID, "SIMULATED_CASH:BRL", LedgerAccountType.ASSET, InstanceCurrency.BRL);
    private static final LedgerAccount CUSTOMER =
            LedgerAccount.forCustomer(CUSTOMER_ID, UUID.randomUUID(), InstanceCurrency.BRL);

    @Mock JournalRepository journalRepository;
    @Mock LedgerAccountRepository ledgerAccountRepository;
    @Mock BalanceProjectionRepository balanceProjectionRepository;

    PostJournal postJournal;

    @BeforeEach
    void setUp() {
        postJournal = new PostJournal(
                Clock.fixed(NOW, ZoneOffset.UTC), journalRepository, ledgerAccountRepository, balanceProjectionRepository);
    }

    private static Money brl(long minorUnits) {
        return new Money(InstanceCurrency.BRL, minorUnits);
    }

    private static EntryCommand debit(LedgerAccountId id, long minorUnits) {
        return new EntryCommand(id, EntrySide.DEBIT, brl(minorUnits));
    }

    private static EntryCommand credit(LedgerAccountId id, long minorUnits) {
        return new EntryCommand(id, EntrySide.CREDIT, brl(minorUnits));
    }

    private static BalanceProjection projection(LedgerAccountId id, long booked) {
        return BalanceProjection.restore(id, brl(booked), brl(0), 0);
    }

    private static PostJournalCommand funding(UUID referenceId, EntryCommand... entries) {
        return new PostJournalCommand(JournalReferenceType.FUNDING, referenceId, List.of(entries));
    }

    private void givenAccountsAndProjections(long cashBooked, long customerBooked) {
        when(ledgerAccountRepository.findAllById(anyCollection())).thenReturn(List.of(CASH, CUSTOMER));
        when(balanceProjectionRepository.lockForUpdate(anyCollection()))
                .thenReturn(List.of(projection(CASH_ID, cashBooked), projection(CUSTOMER_ID, customerBooked)));
    }

    private void assertNothingWritten() {
        verify(journalRepository, never()).save(any());
        verify(balanceProjectionRepository, never()).update(any());
    }

    /** LED-UC-02 #1 — funding grava journal e atualiza as duas projeções. */
    @Test
    void led_uc_02_1_grava_journal_e_atualiza_projecoes() {
        givenAccountsAndProjections(0, 0);
        UUID referenceId = UUID.randomUUID();

        JournalId journalId = postJournal.post(funding(referenceId, debit(CASH_ID, 10_000), credit(CUSTOMER_ID, 10_000)));

        ArgumentCaptor<Journal> journal = ArgumentCaptor.forClass(Journal.class);
        verify(journalRepository).save(journal.capture());
        assertThat(journal.getValue().getId()).isEqualTo(journalId);
        assertThat(journal.getValue().getReferenceType()).isEqualTo(JournalReferenceType.FUNDING);
        assertThat(journal.getValue().getReferenceId()).isEqualTo(referenceId);
        assertThat(journal.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(journal.getValue().getEntries()).containsExactlyInAnyOrder(
                new JournalEntry(CASH_ID, EntrySide.DEBIT, brl(10_000)),
                new JournalEntry(CUSTOMER_ID, EntrySide.CREDIT, brl(10_000)));

        ArgumentCaptor<BalanceProjection> updated = ArgumentCaptor.forClass(BalanceProjection.class);
        verify(balanceProjectionRepository, times(2)).update(updated.capture());
        assertThat(updated.getAllValues())
                .extracting(BalanceProjection::getLedgerAccountId, BalanceProjection::getBooked)
                .containsExactlyInAnyOrder(
                        tuple(CASH_ID, brl(10_000)),       // ASSET: débito aumenta
                        tuple(CUSTOMER_ID, brl(10_000)));  // LIABILITY: crédito aumenta
    }

    /** LED-UC-02 passos 2 e 3 — carrega as contas dos entries e trava as projeções dessas contas. */
    @Test
    void led_uc_02_carrega_as_contas_dos_entries_e_trava_as_projecoes_delas() {
        givenAccountsAndProjections(0, 0);

        postJournal.post(funding(UUID.randomUUID(), debit(CASH_ID, 10_000), credit(CUSTOMER_ID, 10_000)));

        verify(ledgerAccountRepository).findAllById(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(CASH_ID, CUSTOMER_ID))));
        verify(balanceProjectionRepository).lockForUpdate(argThat(accounts -> accounts.size() == 2 && accounts.containsAll(List.of(CASH, CUSTOMER))));
    }

    /** LED-UC-02 passo 6 — journal é gravado antes das projeções. */
    @Test
    void led_uc_02_grava_journal_antes_das_projecoes() {
        givenAccountsAndProjections(0, 0);

        postJournal.post(funding(UUID.randomUUID(), debit(CASH_ID, 10_000), credit(CUSTOMER_ID, 10_000)));

        InOrder inOrder = inOrder(journalRepository, balanceProjectionRepository);
        inOrder.verify(journalRepository).save(any());
        inOrder.verify(balanceProjectionRepository, times(2)).update(any());
    }

    /** LED-UC-02 — cada lançamento gera um JournalId novo. */
    @Test
    void led_uc_02_cada_lancamento_gera_um_journal_id_novo() {
        givenAccountsAndProjections(0, 0);

        JournalId first = postJournal.post(funding(UUID.randomUUID(), debit(CASH_ID, 100), credit(CUSTOMER_ID, 100)));
        JournalId second = postJournal.post(funding(UUID.randomUUID(), debit(CASH_ID, 100), credit(CUSTOMER_ID, 100)));

        assertThat(first).isNotEqualTo(second);
    }

    /** LED-UC-02 #3 — desbalanceado falha antes de tocar no banco (nem carrega, nem trava). */
    @Test
    void led_uc_02_3_desbalanceado_falha_sem_tocar_no_banco() {
        assertThatThrownBy(() -> postJournal.post(
                funding(UUID.randomUUID(), debit(CASH_ID, 10_000), credit(CUSTOMER_ID, 9_000))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(journalRepository, ledgerAccountRepository, balanceProjectionRepository);
    }

    /** LED-UC-02 #4 — conta inexistente: IllegalArgumentException, não trava nem grava. */
    @Test
    void led_uc_02_4_conta_inexistente_falha_sem_travar_nem_gravar() {
        when(ledgerAccountRepository.findAllById(anyCollection())).thenReturn(List.of(CASH));

        assertThatThrownBy(() -> postJournal.post(
                funding(UUID.randomUUID(), debit(CASH_ID, 10_000), credit(CUSTOMER_ID, 10_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CUSTOMER_ID.toString());

        verify(balanceProjectionRepository, never()).lockForUpdate(any());
        assertNothingWritten();
    }

    /** LED-UC-02 #4b — conta sem projeção: IllegalStateException (dado inconsistente), nada gravado. */
    @Test
    void led_uc_02_4b_conta_sem_projecao_e_dado_inconsistente() {
        when(ledgerAccountRepository.findAllById(anyCollection())).thenReturn(List.of(CASH, CUSTOMER));
        when(balanceProjectionRepository.lockForUpdate(anyCollection())).thenReturn(List.of(projection(CASH_ID, 0)));

        assertThatThrownBy(() -> postJournal.post(
                funding(UUID.randomUUID(), debit(CASH_ID, 10_000), credit(CUSTOMER_ID, 10_000))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CUSTOMER_ID.toString());

        assertNothingWritten();
    }

    /** LED-UC-02 #6 — débito que deixa o cliente negativo: InsufficientFundsException, nada gravado. */
    @Test
    void led_uc_02_6_saldo_insuficiente_nao_grava_nada() {
        givenAccountsAndProjections(0, 0);

        assertThatThrownBy(() -> postJournal.post(
                funding(UUID.randomUUID(), debit(CUSTOMER_ID, 10_000), credit(CASH_ID, 10_000))))
                .isInstanceOf(InsufficientFundsException.class);

        assertNothingWritten();
    }

    /** LED-UC-02 #6 (borda) — débito que zera o saldo do cliente é permitido. */
    @Test
    void led_uc_02_6_debito_que_zera_o_saldo_e_permitido() {
        givenAccountsAndProjections(0, 10_000);

        postJournal.post(funding(UUID.randomUUID(), debit(CUSTOMER_ID, 10_000), credit(CASH_ID, 10_000)));

        ArgumentCaptor<BalanceProjection> updated = ArgumentCaptor.forClass(BalanceProjection.class);
        verify(balanceProjectionRepository, times(2)).update(updated.capture());
        assertThat(updated.getAllValues())
                .filteredOn(p -> p.getLedgerAccountId().equals(CUSTOMER_ID))
                .singleElement()
                .extracting(BalanceProjection::getBooked)
                .isEqualTo(brl(0));
    }

    /** Comando nulo é erro de quem chama. */
    @Test
    void comando_nulo_lanca_npe() {
        assertThatThrownBy(() -> postJournal.post(null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(journalRepository, ledgerAccountRepository, balanceProjectionRepository);
    }
}
