package com.hexbank.ledger.application.port.in.view;

import com.hexbank.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

public record LedgerBalanceView(
        UUID accountId,
        Money booked,
        Money reserved,
        Money available,
        Instant asOf
) {
}
