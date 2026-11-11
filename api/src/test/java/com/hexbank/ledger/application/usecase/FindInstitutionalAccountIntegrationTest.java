package com.hexbank.ledger.application.usecase;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.ledger.application.port.in.FindInstitutionalAccountQuery;
import com.hexbank.ledger.domain.LedgerAccountId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LED-UC-04 — Localizar conta institucional. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md#led-uc-04--localizar-conta-institucional.
 * Sem @Transactional no teste: só leitura, a transação (readOnly) vem do próprio caso de uso.
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
class FindInstitutionalAccountIntegrationTest {

    private static final LedgerAccountId CASH_ID =
            new LedgerAccountId(UUID.fromString("00000000-0000-0000-0000-00000000ca5e"));

    @Autowired
    FindInstitutionalAccountQuery query;

    @Test
    void led_uc_04_1_localiza_o_caixa_simulado_criado_pela_v3() {
        assertThat(query.byCode("SIMULATED_CASH:BRL")).isEqualTo(CASH_ID);
    }

    @Test
    void led_uc_04_2_codigo_inexistente_e_erro_de_configuracao() {
        assertThatThrownBy(() -> query.byCode("X"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X");
    }
}
