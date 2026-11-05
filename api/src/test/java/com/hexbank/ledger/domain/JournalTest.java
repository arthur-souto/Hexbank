package com.hexbank.ledger.domain;

import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Especificação: docs/architecture/ARCHITECTURE.md, seção "Journal" (BR-010, BR-011, BR-012, ADR-0002). */
class JournalTest {

    private static final JournalId ID = new JournalId(UUID.fromString("b7e1c3a0-1d2f-4c5b-8a9e-0f1e2d3c4b5a"));
    private static final JournalId REVERSAL_ID = new JournalId(UUID.fromString("c8f2d4b1-2e3a-4d6c-9b0f-1a2b3c4d5e6f"));
    private static final UUID FUNDING_ID = UUID.fromString("d9a3e5c2-3f4b-4e7d-8c1a-2b3c4d5e6f70");
    private static final Instant CREATED_AT = Instant.parse("2026-11-05T12:00:00Z");
    private static final Instant REVERSED_AT = Instant.parse("2026-11-05T13:00:00Z");

    // Mesmo UUID fixo da V3__ledger.sql.
    private static final LedgerAccountId SIMULATED_CASH = LedgerAccountId.of("00000000-0000-0000-0000-00000000ca5e");
    private static final LedgerAccountId CUSTOMER = LedgerAccountId.of("3f1c9a2e-7b4d-4e8a-9c6f-1a2b3c4d5e6f");
    private static final LedgerAccountId OTHER_CUSTOMER = LedgerAccountId.of("4a2d0b3f-8c5e-4f9b-0d7a-2b3c4d5e6f70");

    private static Money brl(long minorUnits) {
        return new Money(InstanceCurrency.BRL, minorUnits);
    }

    private static JournalEntry debit(LedgerAccountId account, long minorUnits) {
        return new JournalEntry(account, EntrySide.DEBIT, brl(minorUnits));
    }

    private static JournalEntry credit(LedgerAccountId account, long minorUnits) {
        return new JournalEntry(account, EntrySide.CREDIT, brl(minorUnits));
    }

    /** Funding de R$ 100: DEBIT caixa simulado / CREDIT cliente (ARCHITECTURE.md, seção Funding). */
    private static List<JournalEntry> fundingEntries() {
        return List.of(debit(SIMULATED_CASH, 10_000), credit(CUSTOMER, 10_000));
    }

    private static Journal post(List<JournalEntry> entries) {
        return Journal.post(ID, JournalReferenceType.FUNDING, FUNDING_ID, CREATED_AT, entries);
    }

    private static Journal funding() {
        return post(fundingEntries());
    }

    @Nested
    class Post {

        @Test
        void criaJournalComOsDadosInformados() {
            Journal journal = funding();

            assertThat(journal.getId()).isEqualTo(ID);
            assertThat(journal.getReferenceType()).isEqualTo(JournalReferenceType.FUNDING);
            assertThat(journal.getReferenceId()).isEqualTo(FUNDING_ID);
            assertThat(journal.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(journal.getEntries())
                    .extracting(JournalEntry::getLedgerAccountId, JournalEntry::getSide, JournalEntry::getAmount)
                    .containsExactly(
                            tuple(SIMULATED_CASH, EntrySide.DEBIT, brl(10_000)),
                            tuple(CUSTOMER, EntrySide.CREDIT, brl(10_000)));
        }

        @Test
        void naoEEstorno() {
            assertThat(funding().getReversalOf()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = JournalReferenceType.class, names = {"FUNDING", "TRANSFER"})
        void aceitaReferenciasDeNegocio(JournalReferenceType type) {
            Journal journal = Journal.post(ID, type, FUNDING_ID, CREATED_AT, fundingEntries());

            assertThat(journal.getReferenceType()).isEqualTo(type);
        }

        @Test
        void rejeitaReversalPorPost() {
            // Estorno só nasce por reverse(), que garante reversalOf preenchido.
            assertThatThrownBy(() -> Journal.post(ID, JournalReferenceType.REVERSAL, FUNDING_ID, CREATED_AT, fundingEntries()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaCamposObrigatoriosNulos() {
            List<JournalEntry> entries = fundingEntries();

            assertThatThrownBy(() -> Journal.post(null, JournalReferenceType.FUNDING, FUNDING_ID, CREATED_AT, entries))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Journal.post(ID, null, FUNDING_ID, CREATED_AT, entries))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Journal.post(ID, JournalReferenceType.FUNDING, null, CREATED_AT, entries))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Journal.post(ID, JournalReferenceType.FUNDING, FUNDING_ID, null, entries))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Journal.post(ID, JournalReferenceType.FUNDING, FUNDING_ID, CREATED_AT, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejeitaEntryNulo() {
            List<JournalEntry> entries = new ArrayList<>(fundingEntries());
            entries.add(null);

            assertThatThrownBy(() -> post(entries)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class MinimoDeEntries {

        @Test
        void rejeitaSemEntries() {
            assertThatThrownBy(() -> post(List.of())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaUmUnicoEntry() {
            assertThatThrownBy(() -> post(List.of(debit(SIMULATED_CASH, 10_000))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aceitaMaisDeDoisEntries() {
            // Ex.: um débito dividido em dois créditos.
            Journal journal = post(List.of(
                    debit(SIMULATED_CASH, 10_000),
                    credit(CUSTOMER, 7_000),
                    credit(OTHER_CUSTOMER, 3_000)));

            assertThat(journal.getEntries()).hasSize(3);
        }
    }

    @Nested
    class Balanceado {

        @Test
        void rejeitaDebitosMaioresQueCreditos() {
            assertThatThrownBy(() -> post(List.of(debit(SIMULATED_CASH, 10_000), credit(CUSTOMER, 9_000))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaCreditosMaioresQueDebitos() {
            assertThatThrownBy(() -> post(List.of(debit(SIMULATED_CASH, 9_000), credit(CUSTOMER, 10_000))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaApenasDebitos() {
            assertThatThrownBy(() -> post(List.of(debit(SIMULATED_CASH, 10_000), debit(CUSTOMER, 10_000))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaApenasCreditos() {
            assertThatThrownBy(() -> post(List.of(credit(SIMULATED_CASH, 10_000), credit(CUSTOMER, 10_000))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void somaComMoneyPlusEOverflowViraExcecao() {
            // Long.MAX_VALUE + 1 nos débitos: com long puro daria a volta para negativo.
            assertThatThrownBy(() -> post(List.of(
                    debit(SIMULATED_CASH, Long.MAX_VALUE),
                    debit(OTHER_CUSTOMER, 1),
                    credit(CUSTOMER, Long.MAX_VALUE))))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    // "Uma moeda": InstanceCurrency só tem BRL no MVP, então ainda não dá para montar um journal multimoeda.
    // Adicionar o teste quando uma segunda moeda entrar no enum.

    @Nested
    class ContaRepetida {

        @Test
        void rejeitaDebitoECreditoNaMesmaConta() {
            // Balanceado, mas é lançamento vazio.
            assertThatThrownBy(() -> post(List.of(debit(CUSTOMER, 10_000), credit(CUSTOMER, 10_000))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaMesmaContaDuasVezesNoMesmoLado() {
            assertThatThrownBy(() -> post(List.of(
                    debit(SIMULATED_CASH, 5_000),
                    debit(SIMULATED_CASH, 5_000),
                    credit(CUSTOMER, 10_000))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Imutavel {

        @Test
        void copiaListaNaCriacao() {
            List<JournalEntry> entries = new ArrayList<>(fundingEntries());
            Journal journal = post(entries);

            entries.add(debit(OTHER_CUSTOMER, 1));

            assertThat(journal.getEntries()).hasSize(2);
        }

        @Test
        void entriesSaoSomenteLeitura() {
            Journal journal = funding();

            assertThatThrownBy(() -> journal.getEntries().add(debit(OTHER_CUSTOMER, 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Estorno {

        @Test
        void geraJournalNovoDeReversal() {
            Journal reversal = funding().reverse(REVERSAL_ID, REVERSED_AT);

            assertThat(reversal.getId()).isEqualTo(REVERSAL_ID);
            assertThat(reversal.getReferenceType()).isEqualTo(JournalReferenceType.REVERSAL);
            assertThat(reversal.getCreatedAt()).isEqualTo(REVERSED_AT);
        }

        @Test
        void apontaParaOOriginal() {
            Journal reversal = funding().reverse(REVERSAL_ID, REVERSED_AT);

            assertThat(reversal.getReversalOf()).contains(ID);
            assertThat(reversal.getReferenceId()).isEqualTo(ID.value());
        }

        @Test
        void mantemContasEValoresEInverteOsLados() {
            Journal reversal = funding().reverse(REVERSAL_ID, REVERSED_AT);

            assertThat(reversal.getEntries())
                    .extracting(JournalEntry::getLedgerAccountId, JournalEntry::getSide, JournalEntry::getAmount)
                    .containsExactly(
                            tuple(SIMULATED_CASH, EntrySide.CREDIT, brl(10_000)),
                            tuple(CUSTOMER, EntrySide.DEBIT, brl(10_000)));
        }

        @Test
        void naoAlteraOOriginal() {
            Journal original = funding();

            original.reverse(REVERSAL_ID, REVERSED_AT);

            assertThat(original.getReferenceType()).isEqualTo(JournalReferenceType.FUNDING);
            assertThat(original.getReversalOf()).isEmpty();
            assertThat(original.getEntries())
                    .extracting(JournalEntry::getSide)
                    .containsExactly(EntrySide.DEBIT, EntrySide.CREDIT);
        }

        @Test
        void proibeEstornarUmEstorno() {
            Journal reversal = funding().reverse(REVERSAL_ID, REVERSED_AT);
            JournalId otherId = new JournalId(UUID.fromString("e0b4f6d3-4a5c-4f8e-9d2b-3c4d5e6f7081"));

            assertThatThrownBy(() -> reversal.reverse(otherId, REVERSED_AT))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejeitaMesmoIdDoOriginal() {
            // Senão o estorno seria estorno de si mesmo (id == reversalOf).
            Journal original = funding();

            assertThatThrownBy(() -> original.reverse(ID, REVERSED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaCamposObrigatoriosNulos() {
            Journal original = funding();

            assertThatThrownBy(() -> original.reverse(null, REVERSED_AT)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> original.reverse(REVERSAL_ID, null)).isInstanceOf(NullPointerException.class);
        }
    }
}
