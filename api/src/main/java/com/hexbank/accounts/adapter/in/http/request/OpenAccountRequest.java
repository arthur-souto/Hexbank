package com.hexbank.accounts.adapter.in.http.request;

import com.hexbank.shared.domain.InstanceCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(
        @Schema(description = "Moeda da conta. Só BRL é suportada.", example = "BRL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        InstanceCurrency currency
) {
}
