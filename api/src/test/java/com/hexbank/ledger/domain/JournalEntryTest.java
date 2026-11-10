package com.hexbank.ledger.domain;

import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Especificação: docs/architecture/ARCHITECTURE.md, seção "JournalEntry" (ADR-0002, seção 1; BR-011). */
class JournalEntryTest {

    private static final LedgerAccountId ACCOUNT = LedgerAccountId.of("3f1c9a2e-7b4d-4e8a-9c6f-1a2b3c4d5e6f");
    private static final Money AMOUNT = new Money(InstanceCurrency.BRL, 10_000);

    @Test
    void criaEntryComOsDadosInformados() {
        JournalEntry entry = new JournalEntry(ACCOUNT, EntrySide.DEBIT, AMOUNT);

        assertThat(entry.ledgerAccountId()).isEqualTo(ACCOUNT);
        assertThat(entry.side()).isEqualTo(EntrySide.DEBIT);
        assertThat(entry.amount()).isEqualTo(AMOUNT);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejeitaValorZeroOuNegativo(long minorUnits) {
        Money amount = new Money(InstanceCurrency.BRL, minorUnits);

        assertThatThrownBy(() -> new JournalEntry(ACCOUNT, EntrySide.CREDIT, amount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaCamposObrigatoriosNulos() {
        assertThatThrownBy(() -> new JournalEntry(null, EntrySide.DEBIT, AMOUNT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JournalEntry(ACCOUNT, null, AMOUNT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JournalEntry(ACCOUNT, EntrySide.DEBIT, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void reversedInverteDebitoParaCredito() {
        JournalEntry reversed = new JournalEntry(ACCOUNT, EntrySide.DEBIT, AMOUNT).reversed();

        assertThat(reversed.side()).isEqualTo(EntrySide.CREDIT);
        assertThat(reversed.ledgerAccountId()).isEqualTo(ACCOUNT);
        assertThat(reversed.amount()).isEqualTo(AMOUNT);
    }

    @Test
    void reversedInverteCreditoParaDebito() {
        JournalEntry reversed = new JournalEntry(ACCOUNT, EntrySide.CREDIT, AMOUNT).reversed();

        assertThat(reversed.side()).isEqualTo(EntrySide.DEBIT);
    }

    @Test
    void reversedNaoAlteraOOriginal() {
        JournalEntry original = new JournalEntry(ACCOUNT, EntrySide.DEBIT, AMOUNT);

        original.reversed();

        assertThat(original.side()).isEqualTo(EntrySide.DEBIT);
    }
}
