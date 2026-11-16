package com.hexbank.accounts.application.usecase;

import com.hexbank.accounts.application.port.in.OpenAccountUseCase;
import com.hexbank.accounts.application.port.in.command.OpenAccountCommand;
import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.ledger.application.port.in.OpenCustomerLedgerAccountUseCase;
import com.hexbank.shared.application.port.out.AuditLogPort;
import com.hexbank.shared.domain.AuditAction;
import com.hexbank.shared.domain.AuditAggregateType;
import com.hexbank.shared.domain.AuditEntry;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class OpenAccount implements OpenAccountUseCase {

    private final AccountRepository accountRepository;
    private final OpenCustomerLedgerAccountUseCase openCustomerLedgerAccountUseCase;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public OpenAccount(AccountRepository accountRepository, OpenCustomerLedgerAccountUseCase openCustomerLedgerAccountUseCase, AuditLogPort auditLogPort, Clock clock) {
        this.accountRepository = accountRepository;
        this.openCustomerLedgerAccountUseCase = openCustomerLedgerAccountUseCase;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Account open(OpenAccountCommand command) {
        Objects.requireNonNull(command.ownerId(), "Owner Id cannot be null");
        Objects.requireNonNull(command.currency(), "Currency cannot be null");
        Objects.requireNonNull(command.requestId(), "Request Id cannot be null");

        AccountId accountId = new AccountId(UUID.randomUUID());
        Instant now = clock.instant();

        Account account = Account.open(
                accountId,
                command.ownerId(),
                command.currency(),
                now
        );

       accountRepository.save(account);

       openCustomerLedgerAccountUseCase.open(
               accountId.value(),
               account.getCurrency()
       );

       AuditEntry auditEntry = new AuditEntry(
                UUID.randomUUID(),
                now,
                command.ownerId().value(),
                AuditAction.ACCOUNT_CREATED,
                AuditAggregateType.ACCOUNT,
                account.getId().value(),
                command.requestId(),
                null,
                null,
                null,
                null
       );

       auditLogPort.record(auditEntry);
       return account;
    }
}
