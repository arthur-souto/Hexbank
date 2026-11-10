package com.hexbank.ledger.application.port.out;

import com.hexbank.ledger.domain.LedgerAccount;
import com.hexbank.ledger.domain.LedgerAccountId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository {
    void save(LedgerAccount account);
    Optional<LedgerAccount> findById(LedgerAccountId id);
    List<LedgerAccount> findAllById(Collection<LedgerAccountId> ids);
    Optional<LedgerAccount> findByAccountId(UUID accountId);
    Optional<LedgerAccount> findByCode(String code);
}
