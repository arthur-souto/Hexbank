package com.hexbank.ledger.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.hexbank.ledger.domain.EntrySide.CREDIT;
import static com.hexbank.ledger.domain.EntrySide.DEBIT;
import static com.hexbank.ledger.domain.LedgerAccountType.ASSET;
import static com.hexbank.ledger.domain.LedgerAccountType.EQUITY;
import static com.hexbank.ledger.domain.LedgerAccountType.LIABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Convenção contábil: docs/adr/0002-convencao-contabil.md (seções 2 e 5). */
class LedgerAccountTypeTest {

    @Nested
    class SaldoNormal {

        @Test
        void ativoCresceComDebito() {
            // Caixa: o banco "tem" o dinheiro.
            assertThat(ASSET.normalBalance()).isEqualTo(DEBIT);
        }

        @Test
        void passivoCresceComCredito() {
            // Conta de cliente: o banco "deve" o dinheiro ao cliente.
            assertThat(LIABILITY.normalBalance()).isEqualTo(CREDIT);
        }

        @Test
        void patrimonioCresceComCredito() {
            assertThat(EQUITY.normalBalance()).isEqualTo(CREDIT);
        }

        @Test
        void mvpTemSoTresTipos() {
            // INCOME/EXPENSE entram por ADR próprio quando houver tarifas (ADR-0002).
            assertThat(LedgerAccountType.values()).containsExactly(ASSET, LIABILITY, EQUITY);
        }
    }

    @Nested
    class Delta {

        @ParameterizedTest(name = "{0} + {1} de {2} → delta {3}")
        @CsvSource({
                // tipo,      lado,   valor, delta esperado
                "ASSET,       DEBIT,  1000,  1000",   // dinheiro entra no caixa
                "ASSET,       CREDIT, 1000, -1000",   // dinheiro sai do caixa
                "LIABILITY,   CREDIT, 1000,  1000",   // banco passa a dever mais ao cliente (saldo dele sobe)
                "LIABILITY,   DEBIT,  1000, -1000",   // banco passa a dever menos (saldo do cliente desce)
                "EQUITY,      CREDIT, 1000,  1000",
                "EQUITY,      DEBIT,  1000, -1000",
        })
        void ladoNormalSomaLadoOpostoSubtrai(LedgerAccountType type, EntrySide side, long amount, long expected) {
            assertThat(type.delta(side, amount)).isEqualTo(expected);
        }

        @Test
        void mesmoLancamentoTemEfeitoOpostoEmAtivoEPassivo() {
            // Um DEBIT aumenta o caixa mas diminui a conta do cliente.
            assertThat(ASSET.delta(DEBIT, 500)).isEqualTo(500);
            assertThat(LIABILITY.delta(DEBIT, 500)).isEqualTo(-500);
        }

        @ParameterizedTest
        @CsvSource({"DEBIT, 0", "CREDIT, 0", "DEBIT, -1", "CREDIT, -1", "DEBIT, -9223372036854775808"})
        void rejeitaValorZeroOuNegativo(EntrySide side, long amount) {
            // Negativo inverteria o efeito do lado em silêncio; zero é lançamento vazio (ADR-0002: entry sempre > 0).
            assertThatThrownBy(() -> ASSET.delta(side, amount))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void maiorValorPossivelNaoEstouraAoNegar() {
            // Com amount >= 0, -amount sempre cabe em long; negateExact nunca lança.
            assertThat(LIABILITY.delta(DEBIT, Long.MAX_VALUE)).isEqualTo(-Long.MAX_VALUE);
        }
    }

    /**
     * Reproduz o exemplo do ADR-0002, seção 4: aplica cada entry somando o delta ao saldo da conta,
     * exatamente como a BalanceProjection fará.
     */
    @Nested
    class ExemploDoAdr0002 {

        private final Map<String, LedgerAccountType> tipos = Map.of(
                "SIMULATED_CASH:BRL", ASSET,
                "cliente A", LIABILITY,
                "cliente B", LIABILITY);

        private final Map<String, Long> saldos = new LinkedHashMap<>(Map.of(
                "SIMULATED_CASH:BRL", 0L,
                "cliente A", 0L,
                "cliente B", 0L));

        private void lancar(String conta, EntrySide lado, long valor) {
            long delta = tipos.get(conta).delta(lado, valor);
            saldos.merge(conta, delta, Math::addExact);
        }

        @Test
        void fundingDe100EntraNoCaixaENoCliente() {
            lancar("SIMULATED_CASH:BRL", DEBIT, 10_000);
            lancar("cliente A", CREDIT, 10_000);

            assertThat(saldos).containsEntry("SIMULATED_CASH:BRL", 10_000L)
                    .containsEntry("cliente A", 10_000L)
                    .containsEntry("cliente B", 0L);
        }

        @Test
        void fundingETransferenciaDeixamTodosOsSaldosPositivos() {
            // Funding de R$ 100,00 para A
            lancar("SIMULATED_CASH:BRL", DEBIT, 10_000);
            lancar("cliente A", CREDIT, 10_000);

            // Transferência de R$ 40,00 de A para B
            lancar("cliente A", DEBIT, 4_000);
            lancar("cliente B", CREDIT, 4_000);

            assertThat(saldos).containsEntry("SIMULATED_CASH:BRL", 10_000L)
                    .containsEntry("cliente A", 6_000L)
                    .containsEntry("cliente B", 4_000L);
        }

        @Test
        void equacaoContabilFecha() {
            // Σ ASSET = Σ LIABILITY + Σ EQUITY (ADR-0002, seção 6)
            lancar("SIMULATED_CASH:BRL", DEBIT, 10_000);
            lancar("cliente A", CREDIT, 10_000);
            lancar("cliente A", DEBIT, 4_000);
            lancar("cliente B", CREDIT, 4_000);

            long ativos = saldos.get("SIMULATED_CASH:BRL");
            long passivos = saldos.get("cliente A") + saldos.get("cliente B");

            assertThat(ativos).isEqualTo(passivos);
        }

        @Test
        void estornoDesfazOLancamento() {
            // Estorno = mesmo journal com lados invertidos (ADR-0002, seção 4).
            lancar("cliente A", DEBIT, 4_000);
            lancar("cliente B", CREDIT, 4_000);

            lancar("cliente A", CREDIT, 4_000);
            lancar("cliente B", DEBIT, 4_000);

            assertThat(saldos).containsEntry("cliente A", 0L).containsEntry("cliente B", 0L);
        }
    }
}
