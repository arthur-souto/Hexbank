package com.hexbank.accounts.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static AccountId of(String raw) {
        return new AccountId(UUID.fromString(raw));
    }

}
