package com.hexbank.shared.domain.exceptions.base;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
