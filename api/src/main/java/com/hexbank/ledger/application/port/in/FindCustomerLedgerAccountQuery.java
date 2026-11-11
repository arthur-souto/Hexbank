package com.hexbank.ledger.application.port.in;

import com.hexbank.ledger.domain.LedgerAccountId;

import java.util.Optional;
import java.util.UUID;

public interface FindCustomerLedgerAccountQuery {
    Optional<LedgerAccountId> byAccountId(UUID accountId);
}
