package com.hexbank.ledger.application.usecase;

import com.hexbank.ledger.application.port.in.FindInstitutionalAccountQuery;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import org.springframework.transaction.annotation.Transactional;

public class FindInstitutionalAccount implements FindInstitutionalAccountQuery {

    private final LedgerAccountRepository repository;

    public FindInstitutionalAccount(LedgerAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerAccountId byCode(String code) {
        return repository.findByCode(code)
                .map(LedgerAccount::getId)
                .orElseThrow(() -> new IllegalStateException("Institutional ledger account not configured: " + code));
    }
}
