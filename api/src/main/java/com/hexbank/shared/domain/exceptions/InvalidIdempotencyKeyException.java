package com.hexbank.shared.domain.exceptions;

import com.hexbank.shared.domain.exceptions.base.BusinessException;

public class InvalidIdempotencyKeyException extends BusinessException {
    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}
