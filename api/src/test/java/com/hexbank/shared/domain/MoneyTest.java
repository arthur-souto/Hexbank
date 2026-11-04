package com.hexbank.shared.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Especificação: docs/architecture/ARCHITECTURE.md, seção "Money — o que deve fazer e respeitar" (BR-001, BR-052).
 *
 * <p>Mistura de moedas (DifferentCurrencyException) não é testável enquanto InstanceCurrency só tem BRL:
 * não há como construir um segundo Money de outra moeda. Adicionar o teste junto da segunda moeda.
 */
class MoneyTest {

    private static final InstanceCurrency BRL = InstanceCurrency.BRL;

    private static Money brl(long minorUnits) {
        return new Money(BRL, minorUnits);
    }

    @Nested
    class Criacao {

        @Test
        void guardaMoedaEUnidadesMenores() {
            Money money = brl(10_000);

            assertThat(money.currency()).isEqualTo(BRL);
            assertThat(money.minorUnits()).isEqualTo(10_000);
        }

        @Test
        void rejeitaMoedaNula() {
            assertThatThrownBy(() -> new Money(null, 100))
                    .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @EnumSource(InstanceCurrency.class)
        void aceitaTodasAsMoedasDeInstanceCurrency(InstanceCurrency currency) {
            // BR-001: o enum é a lista de moedas suportadas; moeda fora dele nem compila.
            assertThat(new Money(currency, 1).currency()).isEqualTo(currency);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE})
        void aceitaZeroNegativoELimitesDoLong(long minorUnits) {
            // Sinal é responsabilidade do chamador (JournalEntry, transferência), não do Money.
            assertThat(brl(minorUnits).minorUnits()).isEqualTo(minorUnits);
        }
    }

    @Nested
    class Igualdade {

        @Test
        void igualdadePorValor() {
            assertThat(brl(500)).isEqualTo(brl(500));
            assertThat(brl(500)).hasSameHashCodeAs(brl(500));
        }

        @Test
        void valoresDiferentesNaoSaoIguais() {
            assertThat(brl(500)).isNotEqualTo(brl(501));
        }
    }

    @Nested
    class IsPositive {

        @Test
        void positivoQuandoMaiorQueZero() {
            assertThat(brl(1).isPositive()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1, Long.MIN_VALUE})
        void naoPositivoQuandoZeroOuNegativo(long minorUnits) {
            assertThat(brl(minorUnits).isPositive()).isFalse();
        }
    }

    @Nested
    class Plus {

        @Test
        void somaUnidadesMenores() {
            assertThat(brl(10_000).plus(brl(4_000))).isEqualTo(brl(14_000));
        }

        @Test
        void mantemAMoeda() {
            assertThat(brl(1).plus(brl(2)).currency()).isEqualTo(BRL);
        }

        @Test
        void naoAlteraOsOperandos() {
            Money a = brl(100);
            Money b = brl(50);

            a.plus(b);

            assertThat(a).isEqualTo(brl(100));
            assertThat(b).isEqualTo(brl(50));
        }

        @Test
        void overflowLancaExcecaoEmVezDeDarValorErrado() {
            assertThatThrownBy(() -> brl(Long.MAX_VALUE).plus(brl(1)))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void underflowLancaExcecao() {
            assertThatThrownBy(() -> brl(Long.MIN_VALUE).plus(brl(-1)))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void somarNoLimiteExatoNaoEstoura() {
            assertThat(brl(Long.MAX_VALUE - 1).plus(brl(1))).isEqualTo(brl(Long.MAX_VALUE));
        }

        @Test
        void rejeitaOperandoNulo() {
            assertThatThrownBy(() -> brl(1).plus(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Minus {

        @Test
        void subtraiUnidadesMenores() {
            assertThat(brl(10_000).minus(brl(4_000))).isEqualTo(brl(6_000));
        }

        @Test
        void podeResultarEmNegativo() {
            // Cálculo intermediário (delta de projeção, booked − reserved) pode ser negativo.
            assertThat(brl(100).minus(brl(300))).isEqualTo(brl(-200));
        }

        @Test
        void naoAlteraOsOperandos() {
            Money a = brl(100);
            Money b = brl(30);

            a.minus(b);

            assertThat(a).isEqualTo(brl(100));
            assertThat(b).isEqualTo(brl(30));
        }

        @Test
        void overflowLancaExcecao() {
            assertThatThrownBy(() -> brl(Long.MIN_VALUE).minus(brl(1)))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void subtrairNegativoNoLimiteEstoura() {
            assertThatThrownBy(() -> brl(Long.MAX_VALUE).minus(brl(-1)))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    /** Propriedades algébricas com valores aleatórios em faixa sem overflow; seed fixa para reprodutibilidade. */
    @Nested
    class Propriedades {

        private static final int CASOS = 1_000;
        private static final long FAIXA = 1_000_000_000_000L; // R$ 10 bilhões, longe do overflow

        private final Random random = new Random(42);

        private Money aleatorio() {
            return brl(random.nextLong(-FAIXA, FAIXA));
        }

        @Test
        void somaEComutativa() {
            for (int i = 0; i < CASOS; i++) {
                Money a = aleatorio();
                Money b = aleatorio();
                assertThat(a.plus(b)).isEqualTo(b.plus(a));
            }
        }

        @Test
        void somaEAssociativa() {
            for (int i = 0; i < CASOS; i++) {
                Money a = aleatorio();
                Money b = aleatorio();
                Money c = aleatorio();
                assertThat(a.plus(b).plus(c)).isEqualTo(a.plus(b.plus(c)));
            }
        }

        @Test
        void zeroENeutro() {
            for (int i = 0; i < CASOS; i++) {
                Money a = aleatorio();
                assertThat(a.plus(brl(0))).isEqualTo(a);
                assertThat(a.minus(brl(0))).isEqualTo(a);
            }
        }

        @Test
        void subtrairDesfazSomar() {
            for (int i = 0; i < CASOS; i++) {
                Money a = aleatorio();
                Money b = aleatorio();
                assertThat(a.plus(b).minus(b)).isEqualTo(a);
            }
        }

        @Test
        void subtrairDeSiMesmoDaZero() {
            for (int i = 0; i < CASOS; i++) {
                Money a = aleatorio();
                assertThat(a.minus(a)).isEqualTo(brl(0));
            }
        }
    }
}
