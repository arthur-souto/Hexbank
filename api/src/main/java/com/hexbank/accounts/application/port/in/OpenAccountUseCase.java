package com.hexbank.accounts.application.port.in;

import com.hexbank.accounts.application.port.in.command.OpenAccountCommand;
import com.hexbank.accounts.domain.Account;

public interface OpenAccountUseCase {
    Account open(OpenAccountCommand command);
}
