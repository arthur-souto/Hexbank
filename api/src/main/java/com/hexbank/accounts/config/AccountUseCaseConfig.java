package com.hexbank.accounts.config;

import com.hexbank.accounts.application.port.in.AccountStatusQuery;
import com.hexbank.accounts.application.port.in.GetAccountQuery;
import com.hexbank.accounts.application.port.in.OpenAccountUseCase;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.application.usecase.CheckAccountActive;
import com.hexbank.accounts.application.usecase.GetAccount;
import com.hexbank.accounts.application.usecase.OpenAccount;
import com.hexbank.ledger.application.port.in.GetLedgerBalanceQuery;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.shared.application.port.out.AuditLogPort;
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

    @Bean
    public OpenAccountUseCase openAccountUseCase(AccountRepository accountRepository, OpenCustomerLedgerAccountUseCase openCustomerLedgerAccountUseCase, AuditLogPort auditLogPort, Clock clock) {
        return new OpenAccount(accountRepository, openCustomerLedgerAccountUseCase, auditLogPort, clock);
    }
}
