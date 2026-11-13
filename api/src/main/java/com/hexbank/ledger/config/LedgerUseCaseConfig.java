package com.hexbank.ledger.config;

import com.hexbank.ledger.application.port.in.*;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.application.port.out.JournalRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.application.usecase.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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

    @Bean
    public PostJournalUseCase postJournalUseCase(Clock clock, JournalRepository journalRepository, LedgerAccountRepository ledgerAccountRepository, BalanceProjectionRepository balanceProjectionRepository) {
        return new PostJournal(clock, journalRepository, ledgerAccountRepository, balanceProjectionRepository);
    }

    @Bean
    public GetLedgerBalanceQuery getLedgerBalanceQuery(Clock clock, LedgerAccountRepository ledgerAccountRepository, BalanceProjectionRepository balanceProjectionRepository) {
        return new GetLedgerBalance(clock, ledgerAccountRepository, balanceProjectionRepository);
    }
}
