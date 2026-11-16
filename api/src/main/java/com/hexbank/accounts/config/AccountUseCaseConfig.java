package com.hexbank.accounts.config;

import com.hexbank.accounts.application.port.in.AccountStatusQuery;
import com.hexbank.accounts.application.port.in.GetAccountQuery;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.application.usecase.CheckAccountActive;
import com.hexbank.accounts.application.usecase.GetAccount;
import com.hexbank.ledger.application.port.in.GetLedgerBalanceQuery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AccountUseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }

    @Bean
    public AccountStatusQuery accountStatusQuery(AccountRepository accountRepository) {
        return new CheckAccountActive(accountRepository);
    }

    @Bean
    public GetAccountQuery getAccount(AccountRepository accountRepository, GetLedgerBalanceQuery getLedgerBalanceQuery) {
        return new GetAccount(accountRepository, getLedgerBalanceQuery);
    }
}
