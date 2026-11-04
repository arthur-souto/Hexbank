package com.hexbank.ledger.domain;

import com.hexbank.shared.domain.InstanceCurrency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Especificação: docs/architecture/ARCHITECTURE.md, seção "LedgerAccount" (BR-016, ADR-0002, ADR-0003). */
class LedgerAccountTest {

    private static final LedgerAccountId ID = LedgerAccountId.of("3f1c9a2e-7b4d-4e8a-9c6f-1a2b3c4d5e6f");
    private static final UUID CUSTOMER_ACCOUNT_ID = UUID.fromString("a5420e4b-1092-4eac-bedd-21e774c4bc5e");
    // Mesmo UUID fixo da V3__ledger.sql.
    private static final LedgerAccountId SIMULATED_CASH_ID = LedgerAccountId.of("00000000-0000-0000-0000-00000000ca5e");
    private static final String SIMULATED_CASH_CODE = "SIMULATED_CASH:BRL";

    private static LedgerAccount customer() {
        return LedgerAccount.forCustomer(ID, CUSTOMER_ACCOUNT_ID, InstanceCurrency.BRL);
    }

    private static LedgerAccount simulatedCash() {
        return LedgerAccount.institutional(SIMULATED_CASH_ID, SIMULATED_CASH_CODE, LedgerAccountType.ASSET, InstanceCurrency.BRL);
    }

    @Nested
    class ForCustomer {

        @Test
        void criaContaComOsDadosInformados() {
            LedgerAccount account = customer();

            assertThat(account.getId()).isEqualTo(ID);
            assertThat(account.getAccountId()).isEqualTo(CUSTOMER_ACCOUNT_ID);
            assertThat(account.getCurrency()).isEqualTo(InstanceCurrency.BRL);
        }

        @Test
        void eSempreLiability() {
            // Conta de cliente = dinheiro que o banco deve ao cliente (ADR-0002, seção 2).
            assertThat(customer().getType()).isEqualTo(LedgerAccountType.LIABILITY);
        }

        @Test
        void naoTemCode() {
            assertThat(customer().getCode()).isNull();
        }

        @Test
        void rejeitaCamposObrigatoriosNulos() {
            assertThatThrownBy(() -> LedgerAccount.forCustomer(null, CUSTOMER_ACCOUNT_ID, InstanceCurrency.BRL)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> LedgerAccount.forCustomer(ID, null, InstanceCurrency.BRL)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> LedgerAccount.forCustomer(ID, CUSTOMER_ACCOUNT_ID, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Institutional {

        @Test
        void criaOCaixaSimuladoDoMvp() {
            LedgerAccount account = simulatedCash();

            assertThat(account.getId()).isEqualTo(SIMULATED_CASH_ID);
            assertThat(account.getCode()).isEqualTo(SIMULATED_CASH_CODE);
            assertThat(account.getType()).isEqualTo(LedgerAccountType.ASSET);
            assertThat(account.getCurrency()).isEqualTo(InstanceCurrency.BRL);
        }

        @Test
        void naoTemAccountId() {
            assertThat(simulatedCash().getAccountId()).isNull();
        }

        @ParameterizedTest
        @EnumSource(LedgerAccountType.class)
        void aceitaQualquerTipoInformado(LedgerAccountType type) {
            LedgerAccount account = LedgerAccount.institutional(ID, "TEST:BRL", type, InstanceCurrency.BRL);

            assertThat(account.getType()).isEqualTo(type);
        }

        @Test
        void rejeitaCamposObrigatoriosNulos() {
            assertThatThrownBy(() -> LedgerAccount.institutional(null, SIMULATED_CASH_CODE, LedgerAccountType.ASSET, InstanceCurrency.BRL)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> LedgerAccount.institutional(ID, null, LedgerAccountType.ASSET, InstanceCurrency.BRL)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> LedgerAccount.institutional(ID, SIMULATED_CASH_CODE, null, InstanceCurrency.BRL)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> LedgerAccount.institutional(ID, SIMULATED_CASH_CODE, LedgerAccountType.ASSET, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void codeNuloTemMensagemClara() {
            // Garante que o null é checado antes do isBlank(); senão vira NPE genérico sem mensagem.
            assertThatThrownBy(() -> LedgerAccount.institutional(ID, null, LedgerAccountType.ASSET, InstanceCurrency.BRL))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("code cannot be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
        void rejeitaCodeEmBranco(String code) {
            // Um code em branco não identifica nenhuma conta institucional.
            assertThatThrownBy(() -> LedgerAccount.institutional(ID, code, LedgerAccountType.ASSET, InstanceCurrency.BRL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("code cannot be blank");
        }
    }

    /** Regra "Exatamente um vínculo": ou accountId ou code, nunca os dois e nunca nenhum (V3: exactly_one_bond). */
    @Nested
    class ExatamenteUmVinculo {

        @Test
        void contaDeClienteTemSoAccountId() {
            LedgerAccount account = customer();

            assertThat(account.getAccountId()).isNotNull();
            assertThat(account.getCode()).isNull();
        }

        @Test
        void contaInstitucionalTemSoCode() {
            LedgerAccount account = simulatedCash();

            assertThat(account.getCode()).isNotNull();
            assertThat(account.getAccountId()).isNull();
        }
    }

    /** BR-016: só conta institucional pode ficar com saldo disponível negativo. */
    @Nested
    class AllowsNegativeAvailable {

        @Test
        void contaDeClienteNaoPode() {
            assertThat(customer().allowsNegativeAvailable()).isFalse();
        }

        @Test
        void caixaSimuladoPode() {
            // O caixa simulado é a origem do funding; não tem limite (ADR-0002).
            assertThat(simulatedCash().allowsNegativeAvailable()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(LedgerAccountType.class)
        void institucionalPodeQualquerQueSejaOTipo(LedgerAccountType type) {
            // A doc decide pelo vínculo (cliente x institucional), não pelo tipo.
            LedgerAccount account = LedgerAccount.institutional(ID, "TEST:BRL", type, InstanceCurrency.BRL);

            assertThat(account.allowsNegativeAvailable()).isTrue();
        }
    }

    @Nested
    class Imutabilidade {

        @Test
        void naoTemSetters() {
            // id, currency e type são imutáveis; type errado inverte lançamentos (ADR-0002).
            Method[] setters = Arrays.stream(LedgerAccount.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .filter(m -> m.getName().startsWith("set"))
                    .toArray(Method[]::new);

            assertThat(setters).isEmpty();
        }

        @Test
        void naoTemStatus() {
            // ADR-0003: o status vive só em accounts.
            boolean hasStatus = Arrays.stream(LedgerAccount.class.getDeclaredFields())
                    .anyMatch(f -> f.getName().toLowerCase().contains("status"));

            assertThat(hasStatus).isFalse();
        }
    }
}
