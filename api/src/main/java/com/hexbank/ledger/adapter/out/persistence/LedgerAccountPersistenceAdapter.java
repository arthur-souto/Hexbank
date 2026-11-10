package com.hexbank.ledger.adapter.out.persistence;

import com.hexbank.ledger.adapter.out.persistence.entity.LedgerAccountJpaEntity;
import com.hexbank.ledger.adapter.out.persistence.jpa.LedgerAccountJpaRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.ledger.domain.LedgerAccountType;
import com.hexbank.shared.domain.InstanceCurrency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LedgerAccountPersistenceAdapter implements LedgerAccountRepository {

    private final LedgerAccountJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(LedgerAccount account) {
        this.repository.save(toEntity(account));
    }

    @Override
    public Optional<LedgerAccount> findById(LedgerAccountId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<LedgerAccount> findAllById(Collection<LedgerAccountId> ids) {
        return repository.findAllById(ids.stream().map(LedgerAccountId::value).toList())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<LedgerAccount> findByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId).map(this::toDomain);
    }

    @Override
    public Optional<LedgerAccount> findByCode(String code) {
        return repository.findByCode(code).map(this::toDomain);
    }

    private LedgerAccountJpaEntity toEntity(LedgerAccount a) {
        return new LedgerAccountJpaEntity(
                a.getId().value(),
                a.getAccountId(),
                a.getCode(),
                a.getCurrency().name(),
                a.getType().name()
        );
    }

    private LedgerAccount toDomain(LedgerAccountJpaEntity e) {
        LedgerAccountId id = new LedgerAccountId(e.getId());
        InstanceCurrency currency = InstanceCurrency.valueOf(e.getCurrency());
        return e.getAccountId() != null
                ? LedgerAccount.forCustomer(id, e.getAccountId(), currency)
                : LedgerAccount.institutional(id, e.getCode(), LedgerAccountType.valueOf(e.getType()), currency);
    }
}
