package com.hexbank.accounts.application.port.in.command;

import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.domain.InstanceCurrency;

public record OpenAccountCommand(
        PrincipalId ownerId,
        InstanceCurrency currency,
        String requestId
) {
}
