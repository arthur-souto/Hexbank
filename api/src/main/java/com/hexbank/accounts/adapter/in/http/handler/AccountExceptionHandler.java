package com.hexbank.accounts.adapter.in.http.handler;

import com.hexbank.accounts.domain.exceptions.AccountNotActiveException;
import com.hexbank.accounts.domain.exceptions.AccountNotFoundException;
import com.hexbank.accounts.domain.exceptions.InvalidStatusTransitionException;
import com.hexbank.shared.adapter.in.http.ProblemResponses;
import com.hexbank.shared.domain.ErrorsCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        return ProblemResponses.respond(
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Conta não encontrada"),
                ErrorsCodes.ACCOUNT_NOT_FOUND.name(), request);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    ResponseEntity<ProblemDetail> handleNotActive(AccountNotActiveException ex, HttpServletRequest request) {
        return ProblemResponses.respond(
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conta não está ativa"),
                ErrorsCodes.ACCOUNT_NOT_ACTIVE.name(), request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        return ProblemResponses.respond(
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Transição de status inválida"),
                ErrorsCodes.INVALID_STATUS_TRANSITION.name(), request);
    }
}
