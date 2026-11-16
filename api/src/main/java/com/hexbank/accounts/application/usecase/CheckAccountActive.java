package com.hexbank.accounts.application.usecase;

import com.hexbank.accounts.application.port.in.AccountStatusQuery;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.accounts.domain.exceptions.AccountNotFoundException;
import com.hexbank.shared.domain.InstanceCurrency;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

public class CheckAccountActive implements AccountStatusQuery {

    private final AccountRepository accountRepository;

    public CheckAccountActive(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InstanceCurrency requireActive(UUID accountId) {
        Objects.requireNonNull(accountId, "Account id cannot be null");

        AccountId id = new AccountId(accountId);
        Account account = accountRepository
                .findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: id " + accountId));
        account.requireActive();

        return account.getCurrency();
    }
}
