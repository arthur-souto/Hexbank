package com.hexbank.ledger.application.port.in.command;

import com.hexbank.ledger.domain.EntrySide;
import com.hexbank.ledger.domain.LedgerAccountId;
import com.hexbank.shared.domain.Money;

public record EntryCommand(
        LedgerAccountId ledgerAccountId,
        EntrySide side,
        Money amount
) {
}
