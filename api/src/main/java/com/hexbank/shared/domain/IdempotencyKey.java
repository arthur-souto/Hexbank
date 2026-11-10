package com.hexbank.shared.domain;

import com.hexbank.shared.domain.exceptions.InvalidIdempotencyKeyException;

public record IdempotencyKey(String value) {

    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new InvalidIdempotencyKeyException("Idempotency key is required");
        }

        if(value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException("Idempotency key must be between %d and %d (was %d)"
                    .formatted(MIN_LENGTH, MAX_LENGTH, value.length())
            );
        }
    }

    public static IdempotencyKey of(String raw) {
        return new IdempotencyKey(raw);
    }

    @Override
    public String toString() {
        return "IdempotencyKey[***]";
    }
}
