package com.hexbank.ledger.adapter.out.persistence;

import com.hexbank.ledger.adapter.out.persistence.entity.BalanceProjectionJpaEntity;
import com.hexbank.ledger.adapter.out.persistence.jpa.BalanceProjectionJpaRepository;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.domain.BalanceProjection;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BalanceProjectionPersistenceAdapter implements BalanceProjectionRepository {

    private final BalanceProjectionJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(BalanceProjection projection) {
        BalanceProjectionJpaEntity entity = new BalanceProjectionJpaEntity(
                projection.getLedgerAccountId().value(),
                projection.getBooked().minorUnits(),
                projection.getReserved().minorUnits()
        );

        repository.save(entity);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<BalanceProjection> lockForUpdate(Collection<LedgerAccount> accounts) {
        Map<UUID, InstanceCurrency> currencyById = accounts.stream()
                .collect(Collectors.toMap(a -> a.getId().value(), LedgerAccount::getCurrency, (a, b) -> a));

        return repository.lockAllOrderById(currencyById.keySet()).stream()
                .map(e -> toDomain(e, currencyById.get(e.getLedgerAccountId())))
                .toList();
    }

    @Override
    public Optional<BalanceProjection> findFor(LedgerAccount ledgerAccount) {
        return repository.findById(ledgerAccount.getId().value())
                .map(e -> toDomain(e, ledgerAccount.getCurrency()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(BalanceProjection projection) {
        UUID id = projection.getLedgerAccountId().value();

        BalanceProjectionJpaEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("balance projection not found: " + id));

        if (entity.getVersion() != projection.getVersion()) {
            throw new OptimisticLockingFailureException("balance projection changed concurrently: " + id);
        }

        entity.update(projection.getBooked().minorUnits(), projection.getReserved().minorUnits());
    }

    private BalanceProjection toDomain(BalanceProjectionJpaEntity e, InstanceCurrency currency) {
        return BalanceProjection.restore(
                new LedgerAccountId(e.getLedgerAccountId()),
                new Money(currency, e.getBookedMinorUnits()),
                new Money(currency, e.getReservedMinorUnits()),
                e.getVersion()
        );
    }
}
