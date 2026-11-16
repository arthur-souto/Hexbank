package com.hexbank.accounts.application.usecase;

import com.hexbank.accounts.application.port.in.GetAccountQuery;
import com.hexbank.accounts.application.port.in.view.AccountBalanceView;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.accounts.domain.exceptions.AccountNotFoundException;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.ledger.application.port.in.GetLedgerBalanceQuery;
import com.hexbank.ledger.application.port.in.view.LedgerBalanceView;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class GetAccount implements GetAccountQuery {

    private final AccountRepository accountRepository;
    private final GetLedgerBalanceQuery getLedgerBalanceQuery;

    public GetAccount(AccountRepository accountRepository, GetLedgerBalanceQuery getLedgerBalanceQuery) {
        this.accountRepository = accountRepository;
        this.getLedgerBalanceQuery = getLedgerBalanceQuery;
    }

    private Account getAndValidateAccount(PrincipalId principal, AccountId id) {
        Objects.requireNonNull(principal, "Principal id cannot be null");
        Objects.requireNonNull(id, "Account id cannot be null");

       return accountRepository.findById(id)
                .filter(a -> a.isOwnedBy(principal))
                .orElseThrow(() -> new AccountNotFoundException("Account not found, id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Account get(PrincipalId principal, AccountId id) {
        return getAndValidateAccount(principal, id);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBalanceView balance(PrincipalId principal, AccountId id) {
        getAndValidateAccount(principal, id);

        LedgerBalanceView balanceView = getLedgerBalanceQuery
                .byAccountId(id.value())
                .orElseThrow(() -> new AccountNotFoundException("Account not found, id: " + id));

        return new AccountBalanceView(
                balanceView.accountId(),
                balanceView.booked(),
                balanceView.reserved(),
                balanceView.available(),
                balanceView.asOf()
        );
    }
}
