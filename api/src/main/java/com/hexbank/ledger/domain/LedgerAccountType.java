package com.hexbank.ledger.domain;

public enum LedgerAccountType {
    ASSET(EntrySide.DEBIT),
    LIABILITY(EntrySide.CREDIT),
    EQUITY(EntrySide.CREDIT);

    private final EntrySide normalBalance;

    LedgerAccountType(EntrySide normalBalance) {
        this.normalBalance = normalBalance;
    }

    public EntrySide normalBalance() {
        return normalBalance;
    }

    public long delta(EntrySide side, long amount) {
        if(amount <= 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        return side == normalBalance ? amount : Math.negateExact(amount);
    }
}
