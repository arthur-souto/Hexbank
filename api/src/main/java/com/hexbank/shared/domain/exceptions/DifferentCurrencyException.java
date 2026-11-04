package com.hexbank.shared.domain.exceptions;

import com.hexbank.shared.domain.exceptions.base.BusinessException;

public class DifferentCurrencyException extends BusinessException {
    public DifferentCurrencyException(String message) {
        super(message);
    }
}
