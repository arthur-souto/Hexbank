package com.hexbank.accounts.domain.exceptions;

import com.hexbank.shared.domain.exceptions.base.BusinessException;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
