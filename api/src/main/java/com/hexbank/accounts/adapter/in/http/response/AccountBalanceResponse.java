package com.hexbank.accounts.adapter.in.http.response;

import com.hexbank.accounts.application.port.in.view.AccountBalanceView;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AccountBalanceResponse(
        @Schema(example = "5b2e8f1a-3c4d-4e6f-9a8b-7c6d5e4f3a2b") UUID accountId,
        @Schema(description = "Saldo contábil em centavos", example = "10000") long bookedMinorUnits,
        @Schema(description = "Valor reservado em centavos", example = "2500") long reservedMinorUnits,
        @Schema(description = "Disponível (booked - reserved) em centavos", example = "7500") long availableMinorUnits,
        @Schema(example = "BRL") String currency,
        @Schema(description = "Momento da leitura do saldo", example = "2026-11-16T12:00:00Z") Instant asOf
) {
    public static AccountBalanceResponse from(AccountBalanceView accountBalanceView) {
        return new AccountBalanceResponse(
                accountBalanceView.accountId(),
                accountBalanceView.booked().minorUnits(),
                accountBalanceView.reserved().minorUnits(),
                accountBalanceView.available().minorUnits(),
                accountBalanceView.booked().currency().name(),
                accountBalanceView.asOf()
        );
    }
}
