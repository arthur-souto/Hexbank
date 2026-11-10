package com.hexbank.ledger.domain.exceptions;

import com.hexbank.shared.domain.exceptions.base.BusinessException;

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
