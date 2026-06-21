package com.threeline.wallet.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends ApiException {
    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
