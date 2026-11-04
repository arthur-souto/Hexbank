package com.hexbank.accounts.application.port.out;

import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;

import java.util.Optional;

public interface AccountRepository {
    void save(Account account);
    Optional<Account> findById(AccountId accountId);
}
