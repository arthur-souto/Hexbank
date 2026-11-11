package com.hexbank.ledger.application.port.in;

import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.InstanceCurrency;

import java.util.UUID;

public interface OpenCustomerLedgerAccountUseCase {
    LedgerAccountId open(UUID accountId, InstanceCurrency currency);
}
