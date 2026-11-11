package com.hexbank.ledger.config;

import com.hexbank.ledger.application.port.in.FindCustomerLedgerAccountQuery;
import com.hexbank.ledger.application.port.in.FindInstitutionalAccountQuery;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.application.usecase.FindCustomerLedgerAccount;
import com.hexbank.ledger.application.usecase.FindInstitutionalAccount;
import com.hexbank.ledger.application.usecase.OpenCustomerLedgerAccount;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerUseCaseConfig {

    @Bean
    public FindInstitutionalAccountQuery findInstitutionalAccountQuery(LedgerAccountRepository repository) {
        return new FindInstitutionalAccount(repository);
    }

    @Bean
    public FindCustomerLedgerAccountQuery findCustomerLedgerAccountQuery(LedgerAccountRepository repository) {
        return new FindCustomerLedgerAccount(repository);
    }

    @Bean
    public OpenCustomerLedgerAccountUseCase openCustomerLedgerAccountUseCase(LedgerAccountRepository ledgerAccountRepository, BalanceProjectionRepository balanceProjectionRepository) {
        return new OpenCustomerLedgerAccount(ledgerAccountRepository, balanceProjectionRepository);
    }
}
