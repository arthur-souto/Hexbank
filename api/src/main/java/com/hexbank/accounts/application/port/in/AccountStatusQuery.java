package com.hexbank.accounts.application.port.in;

import com.hexbank.shared.domain.InstanceCurrency;

import java.util.UUID;

public interface AccountStatusQuery {
    InstanceCurrency requireActive(UUID accountId);
}
