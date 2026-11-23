package com.hexbank.accounts.adapter.in.http.response;

import com.hexbank.accounts.domain.Account;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AccountResponse(
        @Schema(example = "5b2e8f1a-3c4d-4e6f-9a8b-7c6d5e4f3a2b") UUID id,
        @Schema(example = "BRL") String currency,
        @Schema(description = "Status da conta", allowableValues = {"ACTIVE", "BLOCKED", "CLOSED"}, example = "ACTIVE") String status
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId().value(),
                account.getCurrency().name(),
                account.getStatus().name()
        );
    }
}
