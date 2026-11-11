package com.hexbank.accounts.application.usecase;

import com.hexbank.accounts.application.port.in.OpenAccountUseCase;
import com.hexbank.accounts.application.port.in.commnd.OpenAccountCommand;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class OpenAccount implements OpenAccountUseCase {

    private final AccountRepository accountRepository;
    private final Clock clock;

    public OpenAccount(AccountRepository accountRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Override
    public Account open(OpenAccountCommand command) {
        AccountId accountId = new AccountId(UUID.randomUUID());

        Account account = Account.open(
                accountId,
                command.ownerId(),
                command.currency(),
                clock.instant()
        );

       accountRepository.save(account);

       return account;
    }
}
