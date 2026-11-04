package com.hexbank.accounts.adapter.out.persistence;

import com.hexbank.accounts.application.port.out.AccountRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.accounts.domain.AccountStatus;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.domain.InstanceCurrency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository repository;

    @Override
    public void save(Account account) {
        AccountJpaEntity entity = repository.findById(account.getId().value()).map(
                        existing -> existing.update(
                        account.getStatus().name(),
                        account.getClosedAt()
                ))
                .orElseGet(() -> toEntity(account));
        repository.save(entity);
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
        return repository.findById(accountId.value())
                .map(this::toDomain);
    }

    private AccountJpaEntity toEntity(Account a) {
        return new AccountJpaEntity(
                a.getId().value(),
                a.getOwnerId().value(),
                a.getCurrency().name(),
                a.getStatus().name(),
                a.getCreatedAt(),
                a.getClosedAt()
        );
    }

    private Account toDomain(AccountJpaEntity accountJpaEntity) {
        return Account.restore(
                new AccountId(accountJpaEntity.getId()),
                new PrincipalId(accountJpaEntity.getOwnerId()),
                InstanceCurrency.valueOf(accountJpaEntity.getCurrency()), AccountStatus.valueOf(accountJpaEntity.getStatus()),
                accountJpaEntity.getCreatedAt(), accountJpaEntity.getClosedAt()
        );
    }
}
