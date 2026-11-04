package com.hexbank.shared.domain.exceptions;

import com.hexbank.shared.domain.exceptions.base.BusinessException;

public class CurrencyNotSupportedException extends BusinessException {
    public CurrencyNotSupportedException(String message) {
        super(message);
    }
}
