package com.hexbank.ledger.domain;

import com.hexbank.ledger.domain.exceptions.InsufficientFundsException;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Especificação: docs/architecture/ARCHITECTURE.md, seção "BalanceProjection" (BR-013, BR-014, BR-016, ADR-0002 seções 5 e 6). */
class BalanceProjectionTest {

    private static final LedgerAccountId L1 = LedgerAccountId.of("3f1c9a2e-7b4d-4e8a-9c6f-1a2b3c4d5e6f");
    private static final LedgerAccountId L2 = LedgerAccountId.of("9b8e7d6c-5a4f-4e3d-8c2b-1a0f9e8d7c6b");
    // Mesmo UUID fixo da V3__ledger.sql.
    private static final LedgerAccountId SIMULATED_CASH_ID = LedgerAccountId.of("00000000-0000-0000-0000-00000000ca5e");

    private static Money brl(long minorUnits) {
        return new Money(InstanceCurrency.BRL, minorUnits);
    }

    private static LedgerAccount customer(LedgerAccountId id) {
        return LedgerAccount.forCustomer(id, UUID.randomUUID(), InstanceCurrency.BRL);
    }

    private static LedgerAccount simulatedCash() {
        return LedgerAccount.institutional(SIMULATED_CASH_ID, "SIMULATED_CASH:BRL", LedgerAccountType.ASSET, InstanceCurrency.BRL);
    }

    private static BalanceProjection projection(LedgerAccountId id, long booked) {
        return BalanceProjection.restore(id, brl(booked), brl(0), 0);
    }

    private static JournalEntry credit(LedgerAccountId id, long minorUnits) {
        return new JournalEntry(id, EntrySide.CREDIT, brl(minorUnits));
    }

    private static JournalEntry debit(LedgerAccountId id, long minorUnits) {
        return new JournalEntry(id, EntrySide.DEBIT, brl(minorUnits));
    }

    /** LED-BP-01 — Projeção zerada na abertura. */
    @Nested
    class ZeroFor {

        @Test
        void contaDeClienteNasceZerada() {
            BalanceProjection projection = BalanceProjection.zeroFor(customer(L1));

            assertThat(projection.getLedgerAccountId()).isEqualTo(L1);
            assertThat(projection.getBooked()).isEqualTo(brl(0));
            assertThat(projection.getReserved()).isEqualTo(brl(0));
            assertThat(projection.available()).isEqualTo(brl(0));
            assertThat(projection.getVersion()).isZero();
        }

        @Test
        void caixaSimuladoNasceZerado() {
            BalanceProjection projection = BalanceProjection.zeroFor(simulatedCash());

            assertThat(projection.getLedgerAccountId()).isEqualTo(SIMULATED_CASH_ID);
            assertThat(projection.getBooked()).isEqualTo(brl(0));
            assertThat(projection.getReserved()).isEqualTo(brl(0));
            assertThat(projection.available()).isEqualTo(brl(0));
            assertThat(projection.getVersion()).isZero();
        }

        @Test
        void rejeitaContaNula() {
            assertThatThrownBy(() -> BalanceProjection.zeroFor(null)).isInstanceOf(NullPointerException.class);
        }
    }

    /** LED-BP-02 — Conta de cliente (LIABILITY): crédito aumenta, débito diminui. */
    @Nested
    class ContaDeCliente {

        @Test
        void creditoAumentaOSaldo() {
            BalanceProjection updated = projection(L1, 0).apply(credit(L1, 10_000), customer(L1));

            assertThat(updated.getBooked()).isEqualTo(brl(10_000));
            assertThat(updated.available()).isEqualTo(brl(10_000));
        }

        @Test
        void debitoDiminuiOSaldo() {
            BalanceProjection updated = projection(L1, 10_000).apply(debit(L1, 4_000), customer(L1));

            assertThat(updated.getBooked()).isEqualTo(brl(6_000));
        }

        @Test
        void mantemVersaoEReservaDaOriginal() {
            BalanceProjection original = BalanceProjection.restore(L1, brl(10_000), brl(2_000), 7);

            BalanceProjection updated = original.apply(credit(L1, 500), customer(L1));

            assertThat(updated.getVersion()).isEqualTo(7);
            assertThat(updated.getReserved()).isEqualTo(brl(2_000));
            assertThat(updated.getLedgerAccountId()).isEqualTo(L1);
        }

        @Test
        void naoAlteraAProjecaoOriginal() {
            BalanceProjection original = projection(L1, 0);

            BalanceProjection updated = original.apply(credit(L1, 10_000), customer(L1));

            assertThat(updated).isNotSameAs(original);
            assertThat(original.getBooked()).isEqualTo(brl(0));
        }
    }

    /** LED-BP-03 — Conta institucional (ASSET): débito aumenta, crédito diminui. */
    @Nested
    class ContaInstitucional {

        @Test
        void debitoAumentaOSaldoDoCaixa() {
            BalanceProjection updated = projection(SIMULATED_CASH_ID, 0)
                    .apply(debit(SIMULATED_CASH_ID, 10_000), simulatedCash());

            assertThat(updated.getBooked()).isEqualTo(brl(10_000));
        }

        @Test
        void creditoPodeDeixarOCaixaNegativo() {
            BalanceProjection updated = projection(SIMULATED_CASH_ID, 0)
                    .apply(credit(SIMULATED_CASH_ID, 10_000), simulatedCash());

            assertThat(updated.getBooked()).isEqualTo(brl(-10_000));
        }
    }

    /** LED-BP-04 — Disponível considera as reservas. */
    @Nested
    class Disponivel {

        @Test
        void descontaAsReservas() {
            BalanceProjection projection = BalanceProjection.restore(L1, brl(10_000), brl(2_000), 0);

            assertThat(projection.available()).isEqualTo(brl(8_000));
        }

        @Test
        void semReservaEhIgualAoContabil() {
            BalanceProjection projection = projection(L1, 10_000);

            assertThat(projection.available()).isEqualTo(projection.getBooked());
        }
    }

    /** LED-BP-05 — Cliente nunca fica com disponível negativo (BR-016). */
    @Nested
    class SaldoInsuficiente {

        @Test
        void recusaDebitoMaiorQueOSaldo() {
            BalanceProjection original = projection(L1, 5_000);

            assertThatThrownBy(() -> original.apply(debit(L1, 6_000), customer(L1)))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(original.getBooked()).isEqualTo(brl(5_000));
        }

        @Test
        void aceitaDebitoNoLimiteExato() {
            BalanceProjection updated = projection(L1, 5_000).apply(debit(L1, 5_000), customer(L1));

            assertThat(updated.getBooked()).isEqualTo(brl(0));
        }

        @Test
        void reservasContamNoLimite() {
            BalanceProjection original = BalanceProjection.restore(L1, brl(10_000), brl(2_000), 0);

            assertThatThrownBy(() -> original.apply(debit(L1, 9_000), customer(L1)))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void institucionalPodeFicarNegativa() {
            BalanceProjection updated = projection(SIMULATED_CASH_ID, 0)
                    .apply(credit(SIMULATED_CASH_ID, 10_000), simulatedCash());

            assertThat(updated.available()).isEqualTo(brl(-10_000));
        }
    }

    /** LED-BP-06 — Entry na projeção errada é bug. */
    @Nested
    class EntryNaProjecaoErrada {

        @Test
        void recusaEntryEContaDeOutraProjecao() {
            BalanceProjection projectionL1 = projection(L1, 10_000);

            assertThatThrownBy(() -> projectionL1.apply(credit(L2, 100), customer(L2)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void recusaContaDiferenteDaProjecao() {
            BalanceProjection projectionL1 = projection(L1, 10_000);

            assertThatThrownBy(() -> projectionL1.apply(credit(L1, 100), customer(L2)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Disabled("Só testável quando InstanceCurrency tiver uma segunda moeda")
        void recusaEntryEmOutraMoeda() {
        }

        @Test
        void rejeitaArgumentosNulos() {
            BalanceProjection projectionL1 = projection(L1, 10_000);

            assertThatThrownBy(() -> projectionL1.apply(null, customer(L1))).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> projectionL1.apply(credit(L1, 100), null)).isInstanceOf(NullPointerException.class);
        }
    }

    /** LED-BP-07 — Overflow nunca vira saldo errado. */
    @Nested
    class Overflow {

        @Test
        void estouroDoLongLancaErro() {
            BalanceProjection projection = projection(L1, Long.MAX_VALUE);

            assertThatThrownBy(() -> projection.apply(credit(L1, 1), customer(L1)))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    /** LED-BP-08 — Reconstituir do banco. */
    @Nested
    class Restore {

        @Test
        void recriaComOsValoresInformados() {
            BalanceProjection projection = BalanceProjection.restore(L1, brl(10_000), brl(0), 3);

            assertThat(projection.getLedgerAccountId()).isEqualTo(L1);
            assertThat(projection.getBooked()).isEqualTo(brl(10_000));
            assertThat(projection.getReserved()).isEqualTo(brl(0));
            assertThat(projection.getVersion()).isEqualTo(3);
        }

        @Test
        void rejeitaReservaNegativa() {
            assertThatThrownBy(() -> BalanceProjection.restore(L1, brl(10_000), brl(-1), 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejeitaVersaoNegativa() {
            assertThatThrownBy(() -> BalanceProjection.restore(L1, brl(10_000), brl(0), -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Disabled("Só testável quando InstanceCurrency tiver uma segunda moeda")
        void rejeitaMoedasDiferentesEntreContabilEReserva() {
        }

        @Test
        void rejeitaArgumentosNulos() {
            assertThatThrownBy(() -> BalanceProjection.restore(null, brl(0), brl(0), 0)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> BalanceProjection.restore(L1, null, brl(0), 0)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> BalanceProjection.restore(L1, brl(0), null, 0)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void naoAplicaBr016AoReconstituir() {
            BalanceProjection projection = BalanceProjection.restore(L1, brl(-500), brl(0), 0);

            assertThat(projection.getBooked()).isEqualTo(brl(-500));
        }
    }
}
