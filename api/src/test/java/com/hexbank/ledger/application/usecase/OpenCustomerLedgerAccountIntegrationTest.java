package com.hexbank.ledger.application.usecase;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.InstanceCurrency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LED-UC-01 — Abrir conta contábil de cliente. Requer Docker (Testcontainers).
 * Especificação: docs/architecture/ARCHITECTURE.md#led-uc-01--abrir-conta-contábil-de-cliente.
 * <p>
 * Sem @Transactional no teste (ver "Testes de rollback e dados append-only"): o ledger não abre
 * transação própria, então cada chamada roda num TransactionTemplate, como faria o caso de uso que
 * o chama. Assim o commit é real e o cenário #2 prova o rollback de verdade. Ids aleatórios e
 * conferência por id, porque nada é limpo entre os testes.
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
class OpenCustomerLedgerAccountIntegrationTest {

    @Autowired
    OpenCustomerLedgerAccountUseCase useCase;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private LedgerAccountId openInTransaction(UUID accountId) {
        return tx.execute(status -> useCase.open(accountId, InstanceCurrency.BRL));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void led_uc_01_1_grava_conta_liability_brl_e_projecao_zerada() {
        UUID accountId = UUID.randomUUID();

        LedgerAccountId id = openInTransaction(accountId);

        assertThat(id).isNotNull();
        Map<String, Object> account = jdbc.queryForMap(
                "SELECT account_id, code, currency, type FROM ledger.ledger_accounts WHERE id = ?", id.value());
        assertThat(account.get("account_id")).isEqualTo(accountId);
        assertThat(account.get("code")).isNull();
        assertThat(account.get("currency")).isEqualTo("BRL");
        assertThat(account.get("type")).isEqualTo("LIABILITY");

        Map<String, Object> projection = jdbc.queryForMap(
                "SELECT booked_minor_units, reserved_minor_units FROM ledger.balance_projections WHERE ledger_account_id = ?",
                id.value());
        assertThat(projection.get("booked_minor_units")).isEqualTo(0L);
        assertThat(projection.get("reserved_minor_units")).isEqualTo(0L);
    }

    @Test
    void led_uc_01_1_cada_abertura_gera_um_id_novo() {
        assertThat(openInTransaction(UUID.randomUUID())).isNotEqualTo(openInTransaction(UUID.randomUUID()));
    }

    @Test
    void led_uc_01_2_accountId_repetido_desfaz_a_abertura_inteira() {
        UUID accountId = UUID.randomUUID();
        LedgerAccountId first = openInTransaction(accountId);
        AtomicReference<LedgerAccountId> second = new AtomicReference<>();

        // A violação pode surgir só no flush/commit, depois de open() devolver: por isso a exceção
        // é esperada do execute(), e não da chamada ao caso de uso.
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                second.set(useCase.open(accountId, InstanceCurrency.BRL))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("SELECT count(*) FROM ledger.ledger_accounts WHERE account_id = ?", accountId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ledger.balance_projections WHERE ledger_account_id = ?", first.value()))
                .isEqualTo(1);
        if (second.get() != null) {
            assertThat(count("SELECT count(*) FROM ledger.ledger_accounts WHERE id = ?", second.get().value())).isZero();
            assertThat(count("SELECT count(*) FROM ledger.balance_projections WHERE ledger_account_id = ?", second.get().value()))
                    .isZero();
        }
    }

    @Test
    void led_uc_01_3_null_e_erro_de_quem_chama() {
        assertThatThrownBy(() -> useCase.open(null, InstanceCurrency.BRL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> useCase.open(UUID.randomUUID(), null)).isInstanceOf(NullPointerException.class);
    }
}
