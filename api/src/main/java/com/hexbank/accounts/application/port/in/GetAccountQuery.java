package com.hexbank.accounts.application.port.in;

import com.hexbank.accounts.application.port.in.commnd.AccountBalanceView;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.identity.domain.PrincipalId;

public interface GetAccountQuery {
    Account get(PrincipalId principal, AccountId id);
    AccountBalanceView balance(PrincipalId principal, AccountId id);
}
