package com.hexbank.ledger.application.port.in;

import com.hexbank.ledger.application.port.in.view.LedgerBalanceView;

import java.util.Optional;
import java.util.UUID;

public interface GetLedgerBalanceQuery {
    Optional<LedgerBalanceView> byAccountId(UUID accountId);
}
