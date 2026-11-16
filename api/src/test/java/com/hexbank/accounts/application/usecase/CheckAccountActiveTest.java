package com.hexbank.accounts.application.usecase;

import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.accounts.domain.AccountStatus;
import com.hexbank.accounts.domain.exceptions.AccountNotActiveException;
import com.hexbank.accounts.domain.exceptions.AccountNotFoundException;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.domain.InstanceCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Especificação: docs/architecture/ARCHITECTURE.md, "ACC-UC-04 — Exigir conta ativa".
 * Teste unitário com o AccountRepository mockado.
 */
@ExtendWith(MockitoExtension.class)
class CheckAccountActiveTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-16T12:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-11-16T13:00:00Z");
    private static final UUID ACCOUNT_UUID = UUID.fromString("5b2e8f1a-3c4d-4e6f-9a8b-7c6d5e4f3a2b");
    private static final AccountId ACCOUNT_ID = new AccountId(ACCOUNT_UUID);
    private static final PrincipalId OWNER = PrincipalId.of("0d9c8b7a-6f5e-4d3c-8b2a-1f0e9d8c7b6a");

    @Mock AccountRepository accountRepository;

    CheckAccountActive checkAccountActive;

    @BeforeEach
    void setUp() {
        checkAccountActive = new CheckAccountActive(accountRepository);
    }

    private static Account accountWith(AccountStatus status) {
        Instant closedAt = status == AccountStatus.CLOSED ? CLOSED_AT : null;
        return Account.restore(ACCOUNT_ID, OWNER, InstanceCurrency.BRL, status, CREATED_AT, closedAt);
    }

    /** #1 — conta ACTIVE em BRL: devolve BRL. */
    @Test
    void acc_uc_04_1_conta_ativa_devolve_a_moeda() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountWith(AccountStatus.ACTIVE)));

        assertThat(checkAccountActive.requireActive(ACCOUNT_UUID)).isEqualTo(InstanceCurrency.BRL);
    }

    /** #2 — conta BLOCKED: AccountNotActiveException. */
    @Test
    void acc_uc_04_2_conta_bloqueada_e_recusada() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountWith(AccountStatus.BLOCKED)));

        assertThatThrownBy(() -> checkAccountActive.requireActive(ACCOUNT_UUID))
                .isInstanceOf(AccountNotActiveException.class);
    }

    /** #2 — conta CLOSED: AccountNotActiveException. */
    @Test
    void acc_uc_04_2_conta_encerrada_e_recusada() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountWith(AccountStatus.CLOSED)));

        assertThatThrownBy(() -> checkAccountActive.requireActive(ACCOUNT_UUID))
                .isInstanceOf(AccountNotActiveException.class);
    }

    /** #3 — UUID inexistente: AccountNotFoundException. */
    @Test
    void acc_uc_04_3_conta_inexistente_lanca_not_found() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkAccountActive.requireActive(ACCOUNT_UUID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    /**
     * #4 — a consulta não confere dono: a conta pertence a OWNER, mas a chamada não recebe principal
     * e devolve a moeda mesmo assim. A autorização é de quem chama (ex.: TEST_ADMIN no funding).
     */
    @Test
    void acc_uc_04_4_nao_confere_dono() {
        Account accountOfOwner = accountWith(AccountStatus.ACTIVE);
        PrincipalId anotherPrincipal = PrincipalId.of("9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f");
        assertThat(accountOfOwner.isOwnedBy(anotherPrincipal)).isFalse();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOfOwner));

        assertThat(checkAccountActive.requireActive(ACCOUNT_UUID)).isEqualTo(InstanceCurrency.BRL);
    }

    /** #5 — null: NullPointerException, sem consultar o repositório. */
    @Test
    void acc_uc_04_5_null_lanca_npe_sem_consultar_repositorio() {
        assertThatThrownBy(() -> checkAccountActive.requireActive(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountRepository);
    }
}
