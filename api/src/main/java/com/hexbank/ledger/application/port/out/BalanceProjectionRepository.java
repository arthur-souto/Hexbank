package com.hexbank.ledger.application.port.out;

import com.hexbank.ledger.domain.BalanceProjection;
import com.hexbank.ledger.domain.LedgerAccount;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BalanceProjectionRepository {
    void insert(BalanceProjection projection);
    List<BalanceProjection> lockForUpdate(Collection<LedgerAccount> accounts);
    Optional<BalanceProjection> findFor(LedgerAccount ledgerAccount);
    void update(BalanceProjection projection);
}
