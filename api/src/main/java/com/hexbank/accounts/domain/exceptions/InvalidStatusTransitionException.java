package com.hexbank.accounts.domain.exceptions;

import com.hexbank.shared.domain.exceptions.base.BusinessException;

public class InvalidStatusTransitionException extends BusinessException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
