package com.hexbank.ledger.domain;

import com.hexbank.shared.domain.InstanceCurrency;

import java.util.Objects;
import java.util.UUID;

public class LedgerAccount {
    private LedgerAccountId id;
    private UUID accountId;
    private String code;
    private InstanceCurrency currency;
    private LedgerAccountType type;

    public boolean allowsNegativeAvailable() {
        return this.code != null;
    }

    public static LedgerAccount forCustomer(LedgerAccountId id, UUID accountId, InstanceCurrency currency) {
        LedgerAccount account = new LedgerAccount();
        account.id = Objects.requireNonNull(id, "LedgerAccountId cannot be null");
        account.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        account.currency = Objects.requireNonNull(currency, "currency cannot be null");
        account.type = LedgerAccountType.LIABILITY;
        return account;
    }

    public static LedgerAccount institutional(LedgerAccountId id, String code, LedgerAccountType type, InstanceCurrency currency) {
        LedgerAccount account = new LedgerAccount();
        account.id = Objects.requireNonNull(id, "LedgerAccountId cannot be null");
        account.code = Objects.requireNonNull(code, "code cannot be null");
        if(code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        account.type = Objects.requireNonNull(type, "type cannot be null");
        account.currency = Objects.requireNonNull(currency, "currency cannot be null");
        return account;
    }

    public LedgerAccountId getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public InstanceCurrency getCurrency() {
        return currency;
    }

    public LedgerAccountType getType() {
        return type;
    }
}
