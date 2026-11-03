package com.hexbank.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record PrincipalId(UUID value) {

    public PrincipalId {
        Objects.requireNonNull(value, "PrincipalId não pode ser nulo");
    }

    public static PrincipalId of(String raw) {
        return new PrincipalId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
