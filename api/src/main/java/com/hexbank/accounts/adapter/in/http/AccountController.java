package com.hexbank.accounts.adapter.in.http;

import com.hexbank.accounts.adapter.in.http.request.OpenAccountRequest;
import com.hexbank.accounts.adapter.in.http.response.AccountBalanceResponse;
import com.hexbank.accounts.adapter.in.http.response.AccountResponse;
import com.hexbank.accounts.application.port.in.GetAccountQuery;
import com.hexbank.accounts.application.port.in.OpenAccountUseCase;
import com.hexbank.accounts.application.port.in.command.OpenAccountCommand;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.adapter.in.http.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class AccountController implements AccountApi {

    private final OpenAccountUseCase openAccountUseCase;
    private final GetAccountQuery getAccountQuery;

    @PostMapping
    @Override
    public ResponseEntity<AccountResponse> open(@RequestBody @Valid OpenAccountRequest request,
                                                @AuthenticationPrincipal PrincipalId principalId,
                                                HttpServletRequest servletRequest) {
        final var command = new OpenAccountCommand(
                principalId,
                request.currency(),
                RequestIdFilter.currentRequestId(servletRequest)
        );

        final var account = openAccountUseCase.open(command);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.getId().value())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Override
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable UUID id,
            @AuthenticationPrincipal PrincipalId principalId) {
        final var accountId = new AccountId(id);
        final var account = getAccountQuery.get(principalId, accountId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}/balance")
    @Override
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @PathVariable UUID id,
            @AuthenticationPrincipal PrincipalId principalId) {
        final var accountId = new AccountId(id);
        final var balance = getAccountQuery.balance(principalId, accountId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(AccountBalanceResponse.from(balance));
    }
}
