package com.hexbank.ledger.domain;

import java.util.Objects;
import java.util.UUID;

public record LedgerAccountId(UUID value) {

    public LedgerAccountId {
        Objects.requireNonNull(value, "LedgerAccountId cannot be null");
    }

    public static LedgerAccountId of(String raw) {
        return new LedgerAccountId(UUID.fromString(raw));
    }
}
