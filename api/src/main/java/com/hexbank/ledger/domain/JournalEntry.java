package com.hexbank.ledger.domain;

import com.hexbank.shared.domain.Money;

import java.util.Objects;

public final class JournalEntry {
    private final LedgerAccountId ledgerAccountId;
    private final EntrySide side;
    private final Money amount;

    public JournalEntry(LedgerAccountId ledgerAccountId, EntrySide side, Money amount) {
        this.ledgerAccountId = Objects.requireNonNull(ledgerAccountId, "ledgerAccountId cannot be null");
        this.side = Objects.requireNonNull(side, "side cannot be null");
        this.amount = checkMoneyIsPositive(amount);
    }

    public JournalEntry reversed() {
        EntrySide oppositeSide = this.side == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
        return new JournalEntry(ledgerAccountId, oppositeSide, amount);
    }

    private Money checkMoneyIsPositive(Money money) {
        Objects.requireNonNull(money, "amount cannot be null");

        if(!money.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }

        return money;
    }

    public LedgerAccountId getLedgerAccountId() {
        return ledgerAccountId;
    }

    public EntrySide getSide() {
        return side;
    }

    public Money getAmount() {
        return amount;
    }
}
