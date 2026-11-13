package com.hexbank.ledger.application.usecase;

import com.hexbank.ledger.application.port.in.GetLedgerBalanceQuery;
import com.hexbank.ledger.application.port.in.view.LedgerBalanceView;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.BalanceProjection;
import com.hexbank.ledger.domain.LedgerAccount;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class GetLedgerBalance implements GetLedgerBalanceQuery {

    private final Clock clock;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final BalanceProjectionRepository balanceProjectionRepository;

    public GetLedgerBalance(Clock clock, LedgerAccountRepository ledgerAccountRepository, BalanceProjectionRepository balanceProjectionRepository) {
        this.clock = clock;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.balanceProjectionRepository = balanceProjectionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LedgerBalanceView> byAccountId(UUID accountId) {
        Objects.requireNonNull(accountId, "account id cannot be null");

        Optional<LedgerAccount> ledgerAccount = ledgerAccountRepository
                .findByAccountId(accountId);

        if(ledgerAccount.isEmpty()) {
            return Optional.empty();
        }

        BalanceProjection balanceProjection = balanceProjectionRepository
                .findFor(ledgerAccount.get())
                .orElseThrow(() -> new IllegalStateException("Balance projection not found for this account, accountId: " + accountId));

        return Optional.of(new LedgerBalanceView(
                accountId,
                balanceProjection.getBooked(),
                balanceProjection.getReserved(),
                balanceProjection.available(),
                clock.instant()
        ));
    }
}
