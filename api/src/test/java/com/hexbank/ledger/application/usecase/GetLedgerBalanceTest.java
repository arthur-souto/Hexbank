package com.hexbank.ledger.application.usecase;

import com.hexbank.ledger.application.port.in.view.LedgerBalanceView;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.BalanceProjection;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Especificação: docs/architecture/ARCHITECTURE.md, "LED-UC-03 — Consultar saldo".
 * Teste unitário com as portas de saída mockadas; o caminho real com Postgres está no GetLedgerBalanceIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class GetLedgerBalanceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final LedgerAccountId LEDGER_ID = LedgerAccountId.of("3f1c9a2e-7b4d-4e8a-9c6f-1a2b3c4d5e6f");
    private static final LedgerAccount ACCOUNT = LedgerAccount.forCustomer(LEDGER_ID, ACCOUNT_ID, InstanceCurrency.BRL);

    @Mock LedgerAccountRepository ledgerAccountRepository;
    @Mock BalanceProjectionRepository balanceProjectionRepository;

    GetLedgerBalance getLedgerBalance;

    @BeforeEach
    void setUp() {
        getLedgerBalance = new GetLedgerBalance(
                Clock.fixed(NOW, ZoneOffset.UTC), ledgerAccountRepository, balanceProjectionRepository);
    }

    private static Money brl(long minorUnits) {
        return new Money(InstanceCurrency.BRL, minorUnits);
    }

    /** #1 — conta com booked 10000 e reserved 0: view completa. */
    @Test
    void led_uc_03_1_devolve_view_com_saldos_da_projecao() {
        when(ledgerAccountRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        when(balanceProjectionRepository.findFor(ACCOUNT))
                .thenReturn(Optional.of(BalanceProjection.restore(LEDGER_ID, brl(10_000), brl(0), 1)));

        Optional<LedgerBalanceView> view = getLedgerBalance.byAccountId(ACCOUNT_ID);

        assertThat(view).contains(new LedgerBalanceView(ACCOUNT_ID, brl(10_000), brl(0), brl(10_000), NOW));
    }

    /** #1 — available vem do domínio (booked - reserved), não é recalculado pelo caso de uso. */
    @Test
    void led_uc_03_1_available_desconta_o_reservado() {
        when(ledgerAccountRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        when(balanceProjectionRepository.findFor(ACCOUNT))
                .thenReturn(Optional.of(BalanceProjection.restore(LEDGER_ID, brl(10_000), brl(3_000), 2)));

        LedgerBalanceView view = getLedgerBalance.byAccountId(ACCOUNT_ID).orElseThrow();

        assertThat(view.booked()).isEqualTo(brl(10_000));
        assertThat(view.reserved()).isEqualTo(brl(3_000));
        assertThat(view.available()).isEqualTo(brl(7_000));
    }

    /** #2 — accountId sem conta contábil: Optional.empty(), sem buscar projeção. */
    @Test
    void led_uc_03_2_conta_inexistente_devolve_vazio() {
        when(ledgerAccountRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThat(getLedgerBalance.byAccountId(ACCOUNT_ID)).isEmpty();

        verifyNoInteractions(balanceProjectionRepository);
    }

    /** #3 — asOf é o instante da leitura (Clock). */
    @Test
    void led_uc_03_3_as_of_e_o_instante_do_clock() {
        when(ledgerAccountRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        when(balanceProjectionRepository.findFor(ACCOUNT))
                .thenReturn(Optional.of(BalanceProjection.zeroFor(ACCOUNT)));

        assertThat(getLedgerBalance.byAccountId(ACCOUNT_ID).orElseThrow().asOf()).isEqualTo(NOW);
    }

    /** #4 — conta existe mas sem projeção: dado inconsistente, IllegalStateException (500). */
    @Test
    void led_uc_03_4_conta_sem_projecao_e_dado_inconsistente() {
        when(ledgerAccountRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        when(balanceProjectionRepository.findFor(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getLedgerBalance.byAccountId(ACCOUNT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ACCOUNT_ID.toString());
    }

    /** #5 — null é erro de quem chama. */
    @Test
    void led_uc_03_5_null_lanca_npe() {
        assertThatThrownBy(() -> getLedgerBalance.byAccountId(null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(ledgerAccountRepository, balanceProjectionRepository);
    }
}
