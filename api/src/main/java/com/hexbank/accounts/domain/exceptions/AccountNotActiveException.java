package com.hexbank.accounts.domain.exceptions;

import com.hexbank.shared.domain.exceptions.base.BusinessException;

public class AccountNotActiveException extends BusinessException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
