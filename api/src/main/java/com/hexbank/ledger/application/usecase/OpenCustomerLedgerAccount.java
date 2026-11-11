package com.hexbank.ledger.application.usecase;

import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.BalanceProjection;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.InstanceCurrency;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

public class OpenCustomerLedgerAccount implements OpenCustomerLedgerAccountUseCase {

    private final LedgerAccountRepository accountRepository;
    private final BalanceProjectionRepository balanceProjectionRepository;

    public OpenCustomerLedgerAccount(LedgerAccountRepository accountRepository, BalanceProjectionRepository balanceProjectionRepository) {
        this.accountRepository = accountRepository;
        this.balanceProjectionRepository = balanceProjectionRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public LedgerAccountId open(UUID accountId, InstanceCurrency currency) {
        Objects.requireNonNull(accountId, "Account id cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");

        LedgerAccountId ledgerAccountId = new LedgerAccountId(UUID.randomUUID());
        LedgerAccount ledgerAccount = LedgerAccount.forCustomer(ledgerAccountId, accountId, currency);
        accountRepository.save(ledgerAccount);

        BalanceProjection balanceProjection = BalanceProjection.zeroFor(ledgerAccount);
        balanceProjectionRepository.insert(balanceProjection);

        return ledgerAccountId;
    }
}
