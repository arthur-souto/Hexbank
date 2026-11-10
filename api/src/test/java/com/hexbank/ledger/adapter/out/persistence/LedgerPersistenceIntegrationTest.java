package com.hexbank.ledger.adapter.out.persistence;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.ledger.adapter.out.persistence.jpa.BalanceProjectionJpaRepository;
import com.hexbank.ledger.domain.BalanceProjection;
import com.hexbank.ledger.domain.EntrySide;
import com.hexbank.ledger.domain.Journal;
import com.hexbank.ledger.domain.JournalEntry;
import com.hexbank.ledger.domain.JournalId;
import com.hexbank.ledger.domain.JournalReferenceType;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.ledger.domain.LedgerAccountType;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V3__ledger.sql + adapters de persistência do ledger. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md, LED-BP-09/10 e LED-UC-01 a 04.
 * Cada teste roda numa transação revertida ao final; por isso as escritas JPA precisam de flush()
 * para que constraints, triggers e o UPDATE condicional por version cheguem ao banco.
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class LedgerPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final LedgerAccountId CASH_ID = new LedgerAccountId(UUID.fromString("00000000-0000-0000-0000-00000000ca5e"));
    private static final String CASH_CODE = "SIMULATED_CASH:BRL";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LedgerAccountPersistenceAdapter ledgerAccounts;

    @Autowired
    JournalPersistenceAdapter journals;

    @Autowired
    BalanceProjectionPersistenceAdapter projections;

    @Autowired
    BalanceProjectionJpaRepository projectionJpa;

    private static Money brl(long minorUnits) {
        return new Money(InstanceCurrency.BRL, minorUnits);
    }

    /** LED-UC-01: conta contábil de cliente + projeção zerada. */
    private LedgerAccount openCustomer(UUID ledgerAccountId) {
        LedgerAccount account = LedgerAccount.forCustomer(new LedgerAccountId(ledgerAccountId), UUID.randomUUID(), InstanceCurrency.BRL);
        ledgerAccounts.save(account);
        projections.insert(BalanceProjection.zeroFor(account));
        flush();
        return account;
    }

    private LedgerAccount openCustomer() {
        return openCustomer(UUID.randomUUID());
    }

    private LedgerAccount cash() {
        return ledgerAccounts.findById(CASH_ID).orElseThrow();
    }

    private Journal funding(LedgerAccount customer, long amount) {
        return funding(UUID.randomUUID(), customer, amount);
    }

    private Journal funding(UUID referenceId, LedgerAccount customer, long amount) {
        return Journal.post(new JournalId(UUID.randomUUID()), JournalReferenceType.FUNDING, referenceId, NOW, List.of(
                new JournalEntry(CASH_ID, EntrySide.DEBIT, brl(amount)),
                new JournalEntry(customer.getId(), EntrySide.CREDIT, brl(amount))));
    }

    /** Os passos 2 a 6 do LED-UC-02, sem o caso de uso: trava, aplica, grava journal e projeções. */
    private void post(Journal journal, LedgerAccount... accounts) {
        Map<LedgerAccountId, LedgerAccount> byId = List.of(accounts).stream()
                .collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));
        Map<LedgerAccountId, BalanceProjection> locked = projections.lockForUpdate(byId.values()).stream()
                .collect(Collectors.toMap(BalanceProjection::getLedgerAccountId, Function.identity()));

        journals.save(journal);
        for (JournalEntry entry : journal.getEntries()) {
            LedgerAccountId id = entry.ledgerAccountId();
            projections.update(locked.get(id).apply(entry, byId.get(id)));
        }
        flush();
    }

    private void flush() {
        projectionJpa.flush();   // flush do contexto inteiro, com tradução de exceções do Spring
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void insertJournal(UUID id, String referenceType, UUID referenceId, UUID reversalOf) {
        jdbc.update("INSERT INTO ledger.journals (id, reference_type, reference_id, created_at, reversal_of) VALUES (?, ?, ?, ?, ?)",
                id, referenceType, referenceId, Timestamp.from(NOW), reversalOf);
    }

    private void insertEntry(UUID journalId, UUID ledgerAccountId, String side, long minorUnits) {
        jdbc.update("INSERT INTO ledger.journal_entries (id, journal_id, ledger_account_id, side, minor_units, currency) VALUES (?, ?, ?, ?, ?, 'BRL')",
                UUID.randomUUID(), journalId, ledgerAccountId, side, minorUnits);
    }

    /** LED-UC-01 e LED-UC-04 — contas contábeis. */
    @Nested
    class ContasContabeis {

        @Test
        void caixaSimuladoExisteComUuidFixo() {
            LedgerAccount cash = ledgerAccounts.findByCode(CASH_CODE).orElseThrow();

            assertThat(cash.getId()).isEqualTo(CASH_ID);
            assertThat(cash.getType()).isEqualTo(LedgerAccountType.ASSET);
            assertThat(cash.getCurrency()).isEqualTo(InstanceCurrency.BRL);
            assertThat(cash.allowsNegativeAvailable()).isTrue();
        }

        @Test
        void caixaSimuladoJaTemProjecaoZerada() {
            BalanceProjection projection = projections.findFor(cash()).orElseThrow();

            assertThat(projection.getBooked()).isEqualTo(brl(0));
            assertThat(projection.getReserved()).isEqualTo(brl(0));
            assertThat(projection.getVersion()).isZero();
        }

        @Test
        void codigoInexistenteRetornaVazio() {
            assertThat(ledgerAccounts.findByCode("X")).isEmpty();
        }

        @Test
        void contaDeClienteFazIdaEVolta() {
            LedgerAccount account = openCustomer();

            LedgerAccount byId = ledgerAccounts.findById(account.getId()).orElseThrow();
            LedgerAccount byAccountId = ledgerAccounts.findByAccountId(account.getAccountId()).orElseThrow();

            assertThat(byId.getAccountId()).isEqualTo(account.getAccountId());
            assertThat(byId.getType()).isEqualTo(LedgerAccountType.LIABILITY);
            assertThat(byId.getCode()).isNull();
            assertThat(byId.allowsNegativeAvailable()).isFalse();
            assertThat(byAccountId.getId()).isEqualTo(account.getId());
        }

        @Test
        void findAllByIdIgnoraIdsInexistentes() {
            LedgerAccount account = openCustomer();

            List<LedgerAccount> found = ledgerAccounts.findAllById(List.of(account.getId(), CASH_ID, new LedgerAccountId(UUID.randomUUID())));

            assertThat(found).extracting(LedgerAccount::getId).containsExactlyInAnyOrder(account.getId(), CASH_ID);
        }

        @Test
        void recusaSegundaContaContabilParaOMesmoAccountId() {
            LedgerAccount first = openCustomer();
            LedgerAccount duplicate = LedgerAccount.forCustomer(new LedgerAccountId(UUID.randomUUID()), first.getAccountId(), InstanceCurrency.BRL);

            ledgerAccounts.save(duplicate);

            assertThatThrownBy(LedgerPersistenceIntegrationTest.this::flush).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void recusaContaComAccountIdECodeAoMesmoTempo() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO ledger.ledger_accounts (id, account_id, code, currency, type) VALUES (?, ?, 'X', 'BRL', 'LIABILITY')",
                    UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("exactly_one_bond");
        }

        @Test
        void recusaContaDeClienteQueNaoEPassivo() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO ledger.ledger_accounts (id, account_id, code, currency, type) VALUES (?, ?, NULL, 'BRL', 'ASSET')",
                    UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("customer_account_is_liability");
        }
    }

    /** LED-UC-02 — gravação de journal e entries. */
    @Nested
    class Journals {

        @Test
        void gravaJournalEEntriesComFkParaOJournal() {
            LedgerAccount customer = openCustomer();
            Journal journal = funding(customer, 10_000);

            journals.save(journal);
            flush();

            UUID journalId = journal.getId().value();
            assertThat(jdbc.queryForObject("SELECT reference_type FROM ledger.journals WHERE id = ?", String.class, journalId))
                    .isEqualTo("FUNDING");
            assertThat(jdbc.queryForList(
                    "SELECT ledger_account_id, side, minor_units, currency FROM ledger.journal_entries WHERE journal_id = ? ORDER BY side DESC",
                    journalId))
                    .containsExactly(
                            Map.of("ledger_account_id", CASH_ID.value(), "side", "DEBIT", "minor_units", 10_000L, "currency", "BRL"),
                            Map.of("ledger_account_id", customer.getId().value(), "side", "CREDIT", "minor_units", 10_000L, "currency", "BRL"));
        }

        @Test
        void recusaSegundoJournalParaAMesmaReferencia() {
            LedgerAccount customer = openCustomer();
            UUID referenceId = UUID.randomUUID();
            journals.save(funding(referenceId, customer, 100));
            flush();

            journals.save(funding(referenceId, customer, 100));

            assertThatThrownBy(LedgerPersistenceIntegrationTest.this::flush)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_journals_reference");
        }

        @Test
        void gravaEstornoApontandoParaOOriginal() {
            Journal original = funding(openCustomer(), 100);
            journals.save(original);
            Journal reversal = original.reverse(new JournalId(UUID.randomUUID()), NOW);

            journals.save(reversal);
            flush();

            assertThat(jdbc.queryForObject("SELECT reversal_of FROM ledger.journals WHERE id = ?", UUID.class, reversal.getId().value()))
                    .isEqualTo(original.getId().value());
        }

        @Test
        void recusaSegundoEstornoDoMesmoOriginal() {
            Journal original = funding(openCustomer(), 100);
            journals.save(original);
            journals.save(original.reverse(new JournalId(UUID.randomUUID()), NOW));
            flush();

            journals.save(original.reverse(new JournalId(UUID.randomUUID()), NOW));

            assertThatThrownBy(LedgerPersistenceIntegrationTest.this::flush)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_journals");
        }

        @Test
        void recusaReversalOfEmJournalQueNaoEEstorno() {
            UUID original = UUID.randomUUID();
            insertJournal(original, "FUNDING", UUID.randomUUID(), null);

            assertThatThrownBy(() -> insertJournal(UUID.randomUUID(), "FUNDING", original, original))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("reversal_matches_type");
        }

        @Test
        void recusaEstornoCujoReferenceIdNaoEOOriginal() {
            UUID original = UUID.randomUUID();
            insertJournal(original, "FUNDING", UUID.randomUUID(), null);

            assertThatThrownBy(() -> insertJournal(UUID.randomUUID(), "REVERSAL", UUID.randomUUID(), original))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("reversal_references_original");
        }

        @Test
        void recusaEntryComValorZero() {
            UUID journalId = UUID.randomUUID();
            insertJournal(journalId, "FUNDING", UUID.randomUUID(), null);

            assertThatThrownBy(() -> insertEntry(journalId, CASH_ID.value(), "DEBIT", 0))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void recusaEntryComLadoDesconhecido() {
            UUID journalId = UUID.randomUUID();
            insertJournal(journalId, "FUNDING", UUID.randomUUID(), null);

            assertThatThrownBy(() -> insertEntry(journalId, CASH_ID.value(), "LEFT", 100))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void recusaEntryDeContaInexistente() {
            UUID journalId = UUID.randomUUID();
            insertJournal(journalId, "FUNDING", UUID.randomUUID(), null);

            assertThatThrownBy(() -> insertEntry(journalId, UUID.randomUUID(), "DEBIT", 100))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_journal_entries_account_currency");
        }

        @Test
        void recusaDoisEntriesDaMesmaContaNoMesmoJournal() {
            UUID journalId = UUID.randomUUID();
            insertJournal(journalId, "FUNDING", UUID.randomUUID(), null);
            insertEntry(journalId, CASH_ID.value(), "DEBIT", 100);

            assertThatThrownBy(() -> insertEntry(journalId, CASH_ID.value(), "CREDIT", 100))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_journal_entries_account_per_journal");
        }
    }

    /** BR-011 — journals e entries são append-only (trigger). */
    @Nested
    class AppendOnly {

        private Journal saved() {
            Journal journal = funding(openCustomer(), 100);
            journals.save(journal);
            flush();
            return journal;
        }

        @Test
        void recusaUpdateEmJournal() {
            UUID id = saved().getId().value();

            assertThatThrownBy(() -> jdbc.update("UPDATE ledger.journals SET created_at = now() WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }

        @Test
        void recusaDeleteEmJournal() {
            UUID id = saved().getId().value();

            assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger.journals WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }

        @Test
        void recusaUpdateEmEntry() {
            UUID id = saved().getId().value();

            assertThatThrownBy(() -> jdbc.update("UPDATE ledger.journal_entries SET minor_units = 1 WHERE journal_id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }

        @Test
        void recusaDeleteEmEntry() {
            UUID id = saved().getId().value();

            assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger.journal_entries WHERE journal_id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }

        @Test
        void recusaTruncate() {
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE ledger.journal_entries"))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }
    }

    /** LED-BP-09, LED-BP-10 e LED-UC-02 #1, #8 — projeções de saldo. */
    @Nested
    class Projecoes {

        @Test
        void insertGravaProjecaoZeradaComVersionZero() {
            LedgerAccount account = openCustomer();

            BalanceProjection projection = projections.findFor(account).orElseThrow();

            assertThat(projection.getBooked()).isEqualTo(brl(0));
            assertThat(projection.getReserved()).isEqualTo(brl(0));
            assertThat(projection.getVersion()).isZero();
        }

        @Test
        void lockForUpdateDevolveEmOrdemCrescenteDeId() {
            LedgerAccount l1 = openCustomer(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            LedgerAccount l2 = openCustomer(UUID.fromString("00000000-0000-0000-0000-000000000002"));

            List<BalanceProjection> locked = projections.lockForUpdate(List.of(l2, l1));

            assertThat(locked).extracting(BalanceProjection::getLedgerAccountId).containsExactly(l1.getId(), l2.getId());
        }

        @Test
        void lockForUpdateOmiteContaSemProjecao() {
            LedgerAccount withoutProjection = LedgerAccount.forCustomer(new LedgerAccountId(UUID.randomUUID()), UUID.randomUUID(), InstanceCurrency.BRL);
            ledgerAccounts.save(withoutProjection);
            flush();

            assertThat(projections.lockForUpdate(List.of(withoutProjection, cash())))
                    .extracting(BalanceProjection::getLedgerAccountId).containsExactly(CASH_ID);
        }

        @Test
        void fundingAtualizaAsDuasProjecoes() {
            LedgerAccount customer = openCustomer();

            post(funding(customer, 10_000), cash(), customer);

            assertThat(projections.findFor(customer).orElseThrow().getBooked()).isEqualTo(brl(10_000));
            assertThat(projections.findFor(cash()).orElseThrow().getBooked()).isEqualTo(brl(10_000));
        }

        @Test
        void gravacaoIncrementaAVersion() {
            LedgerAccount customer = openCustomer();

            post(funding(customer, 100), cash(), customer);

            assertThat(count("SELECT version FROM ledger.balance_projections WHERE ledger_account_id = ?", customer.getId().value()))
                    .isEqualTo(1);
        }

        @Test
        void recusaProjecaoComVersionDiferenteDaDoBanco() {
            LedgerAccount customer = openCustomer();
            projections.lockForUpdate(List.of(customer));
            BalanceProjection stale = BalanceProjection.restore(customer.getId(), brl(500), brl(0), 7);

            assertThatThrownBy(() -> projections.update(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        void updateCondicionalFalhaSeOBancoMudouDepoisDaLeitura() {
            LedgerAccount customer = openCustomer();
            BalanceProjection read = projections.lockForUpdate(List.of(customer)).getFirst();
            // Outra escrita muda a linha por fora do contexto JPA: o banco vai para version = 1.
            jdbc.update("UPDATE ledger.balance_projections SET version = version + 1 WHERE ledger_account_id = ?", customer.getId().value());

            projections.update(BalanceProjection.restore(customer.getId(), brl(500), brl(0), read.getVersion()));

            assertThatThrownBy(LedgerPersistenceIntegrationTest.this::flush).isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        void saldoRecalculadoPelosEntriesBateComAProjecao() {
            LedgerAccount customer = openCustomer();
            post(funding(customer, 10_000), cash(), customer);
            post(funding(customer, 2_500), cash(), customer);

            long customerFromEntries = count("""
                    SELECT COALESCE(SUM(CASE side WHEN 'CREDIT' THEN minor_units ELSE -minor_units END), 0)
                    FROM ledger.journal_entries WHERE ledger_account_id = ?""", customer.getId().value());
            long cashFromEntries = count("""
                    SELECT COALESCE(SUM(CASE side WHEN 'DEBIT' THEN minor_units ELSE -minor_units END), 0)
                    FROM ledger.journal_entries WHERE ledger_account_id = ?""", CASH_ID.value());

            assertThat(projections.findFor(customer).orElseThrow().getBooked()).isEqualTo(brl(customerFromEntries)).isEqualTo(brl(12_500));
            assertThat(projections.findFor(cash()).orElseThrow().getBooked()).isEqualTo(brl(cashFromEntries)).isEqualTo(brl(12_500));
        }

        @Test
        void recusaReservedNegativo() {
            LedgerAccount customer = openCustomer();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE ledger.balance_projections SET reserved_minor_units = -1 WHERE ledger_account_id = ?", customer.getId().value()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    /** As escritas exigem a transação de quem chama (Propagation.MANDATORY). */
    @Nested
    class TransacaoObrigatoria {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void journalSemTransacaoERecusado() {
            Journal journal = Journal.post(new JournalId(UUID.randomUUID()), JournalReferenceType.FUNDING, UUID.randomUUID(), NOW, List.of(
                    new JournalEntry(CASH_ID, EntrySide.DEBIT, brl(1)),
                    new JournalEntry(new LedgerAccountId(UUID.randomUUID()), EntrySide.CREDIT, brl(1))));

            assertThatThrownBy(() -> journals.save(journal)).isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void contaContabilSemTransacaoERecusada() {
            LedgerAccount account = LedgerAccount.forCustomer(new LedgerAccountId(UUID.randomUUID()), UUID.randomUUID(), InstanceCurrency.BRL);

            assertThatThrownBy(() -> ledgerAccounts.save(account)).isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void lockSemTransacaoERecusado() {
            LedgerAccount cash = ledgerAccounts.findById(CASH_ID).orElseThrow();

            assertThatThrownBy(() -> projections.lockForUpdate(List.of(cash))).isInstanceOf(IllegalTransactionStateException.class);
        }
    }
}
