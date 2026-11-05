package com.hexbank.ledger.domain;

import java.util.Objects;
import java.util.UUID;

public record JournalId(UUID value) {

    public JournalId {
        Objects.requireNonNull(value, "JournalId cannot be null");
    }
}
