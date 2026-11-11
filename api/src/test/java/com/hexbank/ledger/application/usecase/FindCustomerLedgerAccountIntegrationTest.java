package com.hexbank.ledger.application.usecase;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.ledger.application.port.in.FindCustomerLedgerAccountQuery;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.InstanceCurrency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LED-UC-05 — Localizar conta contábil do cliente. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md#led-uc-05--localizar-conta-contábil-do-cliente.
 * Com @Transactional: o cenário #1 grava a conta direto pela porta (save exige MANDATORY),
 * já que o LED-UC-01 ainda não existe. Trocar pelo open() real quando ele estiver pronto.
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class FindCustomerLedgerAccountIntegrationTest {

    @Autowired
    FindCustomerLedgerAccountQuery query;

    @Autowired
    LedgerAccountRepository ledgerAccounts;

    @Test
    void led_uc_05_1_traduz_accountId_para_a_conta_contabil() {
        UUID accountId = UUID.randomUUID();
        LedgerAccountId ledgerAccountId = new LedgerAccountId(UUID.randomUUID());
        ledgerAccounts.save(LedgerAccount.forCustomer(ledgerAccountId, accountId, InstanceCurrency.BRL));

        assertThat(query.byAccountId(accountId)).contains(ledgerAccountId);
    }

    @Test
    void led_uc_05_2_accountId_sem_conta_contabil_devolve_vazio() {
        assertThat(query.byAccountId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void led_uc_05_3_null_e_erro_de_quem_chama() {
        assertThatThrownBy(() -> query.byAccountId(null))
                .isInstanceOf(NullPointerException.class);
    }
}
