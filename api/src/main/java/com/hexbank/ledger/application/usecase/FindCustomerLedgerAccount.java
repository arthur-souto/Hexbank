package com.hexbank.ledger.application.usecase;

import com.hexbank.ledger.application.port.in.FindCustomerLedgerAccountQuery;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class FindCustomerLedgerAccount implements FindCustomerLedgerAccountQuery {

    private final LedgerAccountRepository repository;

    public FindCustomerLedgerAccount(LedgerAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LedgerAccountId> byAccountId(UUID accountId) {
        Objects.requireNonNull(accountId, "account id cannot be null");
        return repository.findByAccountId(accountId).map(LedgerAccount::getId);
    }
}
