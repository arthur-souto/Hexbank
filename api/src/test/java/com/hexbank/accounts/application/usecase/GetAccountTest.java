package com.hexbank.accounts.application.usecase;

import com.hexbank.accounts.application.port.in.view.AccountBalanceView;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.accounts.domain.AccountStatus;
import com.hexbank.accounts.domain.exceptions.AccountNotFoundException;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.ledger.application.port.in.GetLedgerBalanceQuery;
import com.hexbank.ledger.application.port.in.view.LedgerBalanceView;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Especificação: docs/architecture/ARCHITECTURE.md, "ACC-UC-02 — Consultar conta" e "ACC-UC-03 — Consultar saldo".
 * Teste unitário com AccountRepository e GetLedgerBalanceQuery mockados. Os cenários de HTTP
 * (400 para id inválido, corpo em minor units) ficam no @WebMvcTest do controller.
 */
@ExtendWith(MockitoExtension.class)
class GetAccountTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-16T12:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-11-16T13:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-11-16T14:00:00Z");
    private static final UUID ACCOUNT_UUID = UUID.fromString("5b2e8f1a-3c4d-4e6f-9a8b-7c6d5e4f3a2b");
    private static final AccountId ACCOUNT_ID = new AccountId(ACCOUNT_UUID);
    private static final PrincipalId OWNER = PrincipalId.of("0d9c8b7a-6f5e-4d3c-8b2a-1f0e9d8c7b6a");
    private static final PrincipalId OTHER = PrincipalId.of("9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f");

    @Mock AccountRepository accountRepository;
    @Mock GetLedgerBalanceQuery getLedgerBalanceQuery;

    GetAccount getAccount;

    @BeforeEach
    void setUp() {
        getAccount = new GetAccount(accountRepository, getLedgerBalanceQuery);
    }

    private static Account accountOfOwner(AccountStatus status) {
        Instant closedAt = status == AccountStatus.CLOSED ? CLOSED_AT : null;
        return Account.restore(ACCOUNT_ID, OWNER, InstanceCurrency.BRL, status, CREATED_AT, closedAt);
    }

    private static Money brl(long minorUnits) {
        return new Money(InstanceCurrency.BRL, minorUnits);
    }

    // ---------- ACC-UC-02: get ----------

    /** #1 — o dono consulta a própria conta e recebe id, moeda e status. */
    @Test
    void acc_uc_02_1_dono_recebe_a_conta() {
        Account account = accountOfOwner(AccountStatus.ACTIVE);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        Account result = getAccount.get(OWNER, ACCOUNT_ID);

        assertThat(result).isSameAs(account);
        assertThat(result.getId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.getCurrency()).isEqualTo(InstanceCurrency.BRL);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    /** #2 — outro principal consulta a conta: 404 (AccountNotFoundException), não 403. */
    @Test
    void acc_uc_02_2_outro_principal_recebe_not_found() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOfOwner(AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> getAccount.get(OTHER, ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    /** #3 — id inexistente: AccountNotFoundException. */
    @Test
    void acc_uc_02_3_conta_inexistente_recebe_not_found() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getAccount.get(OWNER, ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    /** #2 e #3 — "de outro dono" e "inexistente" são indistinguíveis, inclusive na mensagem. */
    @Test
    void acc_uc_02_2_e_3_outro_dono_e_inexistente_tem_a_mesma_mensagem() {
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(accountOfOwner(AccountStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        Throwable otherOwner = catchThrowable(() -> getAccount.get(OTHER, ACCOUNT_ID));
        Throwable notFound = catchThrowable(() -> getAccount.get(OWNER, ACCOUNT_ID));

        assertThat(otherOwner).isInstanceOf(AccountNotFoundException.class);
        assertThat(notFound).isInstanceOf(AccountNotFoundException.class);
        assertThat(otherOwner.getMessage()).isEqualTo(notFound.getMessage());
        assertThat(otherOwner.getMessage()).doesNotContain(OTHER.value().toString());
    }

    /** #4 — a consulta funciona em qualquer status (ADR-0003). */
    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BLOCKED", "CLOSED"})
    void acc_uc_02_4_conta_bloqueada_ou_encerrada_e_devolvida(AccountStatus status) {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOfOwner(status)));

        assertThat(getAccount.get(OWNER, ACCOUNT_ID).getStatus()).isEqualTo(status);
    }

    /** Pré-condição — principal ou id nulos: NullPointerException, sem consultar o repositório. */
    @Test
    void acc_uc_02_null_lanca_npe_sem_consultar_repositorio() {
        assertThatThrownBy(() -> getAccount.get(null, ACCOUNT_ID)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> getAccount.get(OWNER, null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountRepository, getLedgerBalanceQuery);
    }

    // ---------- ACC-UC-03: balance ----------

    /** #1 — conta nova: saldos zerados vindos do ledger. */
    @Test
    void acc_uc_03_1_conta_nova_tem_saldo_zerado() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOfOwner(AccountStatus.ACTIVE)));
        when(getLedgerBalanceQuery.byAccountId(ACCOUNT_UUID))
                .thenReturn(Optional.of(new LedgerBalanceView(ACCOUNT_UUID, brl(0), brl(0), brl(0), AS_OF)));

        AccountBalanceView view = getAccount.balance(OWNER, ACCOUNT_ID);

        assertThat(view).isEqualTo(new AccountBalanceView(ACCOUNT_UUID, brl(0), brl(0), brl(0), AS_OF));
    }

    /**
     * #2 e #4 — os valores vêm do ledger, sem recálculo. O available propositalmente não é
     * booked - reserved: se o accounts recalculasse, o teste quebraria.
     */
    @Test
    void acc_uc_03_4_copia_a_view_do_ledger_sem_calcular() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOfOwner(AccountStatus.ACTIVE)));
        when(getLedgerBalanceQuery.byAccountId(ACCOUNT_UUID))
                .thenReturn(Optional.of(new LedgerBalanceView(ACCOUNT_UUID, brl(10_000), brl(3_000), brl(1_234), AS_OF)));

        AccountBalanceView view = getAccount.balance(OWNER, ACCOUNT_ID);

        assertThat(view).isEqualTo(new AccountBalanceView(ACCOUNT_UUID, brl(10_000), brl(3_000), brl(1_234), AS_OF));
    }

    /** #3 — conta de outro dono: AccountNotFoundException e o ledger nem é consultado. */
    @Test
    void acc_uc_03_3_outro_dono_recebe_not_found_sem_consultar_ledger() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOfOwner(AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> getAccount.balance(OTHER, ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(getLedgerBalanceQuery);
    }

    /** #3 — conta inexistente: AccountNotFoundException e o ledger nem é consultado. */
    @Test
    void acc_uc_03_3_conta_inexistente_recebe_not_found_sem_consultar_ledger() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getAccount.balance(OWNER, ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(getLedgerBalanceQuery);
    }

    /** LED-UC-03 #2 — o ledger não tem conta contábil: o accounts transforma em AccountNotFoundException. */
    @Test
    void acc_uc_03_ledger_sem_conta_contabil_vira_not_found() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOfOwner(AccountStatus.ACTIVE)));
        when(getLedgerBalanceQuery.byAccountId(ACCOUNT_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getAccount.balance(OWNER, ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    /** Pré-condição — principal ou id nulos: NullPointerException, sem consultar nada. */
    @Test
    void acc_uc_03_null_lanca_npe_sem_consultar_nada() {
        assertThatThrownBy(() -> getAccount.balance(null, ACCOUNT_ID)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> getAccount.balance(OWNER, null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountRepository, getLedgerBalanceQuery);
    }
}
