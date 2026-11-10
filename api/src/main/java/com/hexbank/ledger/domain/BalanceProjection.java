package com.hexbank.ledger.domain;

import com.hexbank.ledger.domain.exceptions.InsufficientFundsException;
import com.hexbank.shared.domain.Money;

import java.util.Objects;

public final class BalanceProjection {
    private final LedgerAccountId ledgerAccountId;
    private final Money booked;
    private final Money reserved;
    private final long version;

    private BalanceProjection(LedgerAccountId id, Money booked, Money reserved, long version) {
        this.ledgerAccountId = Objects.requireNonNull(id, "ledgerAccountId cannot be null");
        this.booked = Objects.requireNonNull(booked, "booked cannot be null");
        this.reserved = Objects.requireNonNull(reserved, "reserved cannot be null");
        if (reserved.minorUnits() < 0) throw new IllegalArgumentException("reserved cannot be negative");
        if (version < 0) throw new IllegalArgumentException("Version cannot be negative");
        if (booked.currency() != reserved.currency()) throw new IllegalArgumentException("Currencies do not match");
        this.version = version;
    }

    public static BalanceProjection zeroFor(LedgerAccount account) {
        Money zero = new Money(account.getCurrency(), 0);
        return new BalanceProjection(account.getId(), zero, zero, 0);
    }

    public static BalanceProjection restore(LedgerAccountId ledgerAccountId, Money booked, Money reserved, long version) {
        return new BalanceProjection(ledgerAccountId, booked, reserved, version);
    }

    public BalanceProjection apply(JournalEntry entry, LedgerAccount account) {
        Objects.requireNonNull(entry, "entry cannot be null");
        Objects.requireNonNull(account, "account cannot be null");

        if (!entry.ledgerAccountId().equals(ledgerAccountId) || !account.getId().equals(ledgerAccountId)) {
            throw new IllegalArgumentException("JournalEntry does not belong to this account");
        }

        if(!entry.amount().currency().equals(reserved.currency())) {
            throw new IllegalArgumentException("JournalEntry currency does not match the balance projection currency");
        }

        final var delta = account.getType().delta(entry.side(), entry.amount().minorUnits());
        Money newBooked = booked.plus(new Money(account.getCurrency(),  delta));

        if(!account.allowsNegativeAvailable() && newBooked.minus(reserved).minorUnits() < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        return new BalanceProjection(ledgerAccountId, newBooked, reserved, version);
    }

    public Money available() {
        return booked.minus(reserved);
    }

    public LedgerAccountId getLedgerAccountId() {
        return ledgerAccountId;
    }

    public Money getBooked() {
        return booked;
    }

    public Money getReserved() {
        return reserved;
    }

    public long getVersion() {
        return version;
    }
}
