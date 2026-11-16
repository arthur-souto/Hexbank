package com.hexbank.accounts.application.usecase;

import com.hexbank.accounts.application.port.in.command.OpenAccountCommand;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountStatus;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.application.port.out.AuditLogPort;
import com.hexbank.shared.domain.AuditAction;
import com.hexbank.shared.domain.AuditAggregateType;
import com.hexbank.shared.domain.AuditEntry;
import com.hexbank.shared.domain.InstanceCurrency;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Especificação: docs/architecture/ARCHITECTURE.md, "ACC-UC-01 — Abrir conta".
 * Teste unitário com as portas mockadas: ordem dos passos, ids repassados e conteúdo da auditoria.
 * Atomicidade (#2 e #6) é provada com Postgres no OpenAccountIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class OpenAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final PrincipalId OWNER = PrincipalId.of("0d9c8b7a-6f5e-4d3c-8b2a-1f0e9d8c7b6a");
    private static final String REQUEST_ID = "req-open-account-0001";

    @Mock AccountRepository accountRepository;
    @Mock OpenCustomerLedgerAccountUseCase openCustomerLedgerAccount;
    @Mock AuditLogPort auditLogPort;

    OpenAccount openAccount;

    @BeforeEach
    void setUp() {
        openAccount = new OpenAccount(accountRepository, openCustomerLedgerAccount, auditLogPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OpenAccountCommand command() {
        return new OpenAccountCommand(OWNER, InstanceCurrency.BRL, REQUEST_ID);
    }

    /** #1 — devolve conta ACTIVE em BRL, do principal do command, criada no instante do Clock. */
    @Test
    void acc_uc_01_1_abre_conta_ativa_do_principal() {
        Account account = openAccount.open(command());

        assertThat(account.getId()).isNotNull();
        assertThat(account.getOwnerId()).isEqualTo(OWNER);
        assertThat(account.getCurrency()).isEqualTo(InstanceCurrency.BRL);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
    }

    /** Ordem da spec: salvar a Account, abrir a conta contábil com o id dela, gravar a auditoria. */
    @Test
    void acc_uc_01_2_salva_abre_ledger_e_audita_nessa_ordem() {
        Account account = openAccount.open(command());

        InOrder inOrder = inOrder(accountRepository, openCustomerLedgerAccount, auditLogPort);
        inOrder.verify(accountRepository).save(account);
        inOrder.verify(openCustomerLedgerAccount).open(account.getId().value(), InstanceCurrency.BRL);
        inOrder.verify(auditLogPort).record(any(AuditEntry.class));
        verifyNoMoreInteractions(accountRepository, openCustomerLedgerAccount, auditLogPort);
    }

    /** #2 — auditoria ACCOUNT_CREATED apontando para a conta, com principal, requestId e o mesmo instante. */
    @Test
    void acc_uc_01_2_auditoria_account_created_aponta_para_a_conta() {
        Account account = openAccount.open(command());

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogPort).record(captor.capture());
        AuditEntry entry = captor.getValue();

        assertThat(entry.id()).isNotNull();
        assertThat(entry.action()).isEqualTo(AuditAction.ACCOUNT_CREATED);
        assertThat(entry.aggregateType()).isEqualTo(AuditAggregateType.ACCOUNT);
        assertThat(entry.aggregateId()).isEqualTo(account.getId().value());
        assertThat(entry.principalId()).isEqualTo(OWNER.value());
        assertThat(entry.requestId()).isEqualTo(REQUEST_ID);
        assertThat(entry.occurredAt()).isEqualTo(account.getCreatedAt()).isEqualTo(NOW);
        assertThat(entry.correlationId()).isNull();
        assertThat(entry.fromStatus()).isNull();
        assertThat(entry.toStatus()).isNull();
        assertThat(entry.reason()).isNull();
    }

    /** #5 — cada abertura gera um id novo (várias contas por dono). */
    @Test
    void acc_uc_01_5_cada_abertura_gera_id_novo() {
        assertThat(openAccount.open(command()).getId()).isNotEqualTo(openAccount.open(command()).getId());
    }

    /** #6 (unitário) — o ledger falha: a exceção sobe e a auditoria não é gravada. O rollback é do integração. */
    @Test
    void acc_uc_01_6_falha_no_ledger_propaga_e_nao_audita() {
        when(openCustomerLedgerAccount.open(any(), any())).thenThrow(new IllegalStateException("falha simulada"));

        assertThatThrownBy(() -> openAccount.open(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha simulada");

        verifyNoInteractions(auditLogPort);
    }

    /** Pré-condição — campos obrigatórios nulos: NullPointerException antes de qualquer gravação. */
    @Test
    void acc_uc_01_campos_nulos_lancam_npe_sem_gravar() {
        assertThatThrownBy(() -> openAccount.open(new OpenAccountCommand(null, InstanceCurrency.BRL, REQUEST_ID)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> openAccount.open(new OpenAccountCommand(OWNER, null, REQUEST_ID)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> openAccount.open(new OpenAccountCommand(OWNER, InstanceCurrency.BRL, null)))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountRepository, openCustomerLedgerAccount, auditLogPort);
    }

    /** Pré-condição — requestId em branco: recusado pelo AuditEntry. Unitário não prova rollback; ver integração. */
    @Test
    void acc_uc_01_request_id_em_branco_e_recusado() {
        assertThatThrownBy(() -> openAccount.open(new OpenAccountCommand(OWNER, InstanceCurrency.BRL, "  ")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(auditLogPort, never()).record(any());
    }
}
