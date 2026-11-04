package com.hexbank.accounts.domain;

import com.hexbank.accounts.domain.exceptions.AccountNotActiveException;
import com.hexbank.accounts.domain.exceptions.InvalidStatusTransitionException;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.domain.InstanceCurrency;

import java.time.Instant;
import java.util.Objects;

public class Account {
    AccountId id;
    PrincipalId ownerId;
    InstanceCurrency currency;
    AccountStatus status;
    Instant createdAt;
    Instant closedAt;

    public static Account open(AccountId accountId, PrincipalId ownerId, InstanceCurrency currency, Instant now) {
        Account account = new Account();
        account.id = Objects.requireNonNull(accountId, "accountId cannot be null");
        account.ownerId = Objects.requireNonNull(ownerId, "ownerId cannot be null");
        account.currency = Objects.requireNonNull(currency, "currency cannot be null");
        account.status = AccountStatus.ACTIVE;
        account.createdAt = Objects.requireNonNull(now, "now cannot be null");
        return account;
    }

    public static Account restore(AccountId accountId, PrincipalId ownerId, InstanceCurrency currency, AccountStatus accountStatus, Instant now, Instant closedAt) {
        Account account = new Account();
        account.id = Objects.requireNonNull(accountId, "accountId cannot be null");
        account.ownerId = Objects.requireNonNull(ownerId, "ownerId cannot be null");
        account.currency = Objects.requireNonNull(currency, "currency cannot be null");
        account.status = Objects.requireNonNull(accountStatus, "accountStatus cannot be null");
        account.createdAt = Objects.requireNonNull(now, "now cannot be null");
        if(accountStatus == AccountStatus.CLOSED) {
            account.closedAt = Objects.requireNonNull(closedAt, "closedAt cannot be null");
        }
        else if (closedAt != null) {
            throw new IllegalArgumentException("closedAt must be null for non-closed accounts");
        }
        return account;
    }

    public boolean isOwnedBy(PrincipalId principalId) {
        return this.ownerId.equals(principalId);
    }

    public void requireActive() {
        if(this.status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Account is not active: " + this.id);
        }
    }

    public void block() {
        if(this.status == AccountStatus.CLOSED) {
            throw new InvalidStatusTransitionException("Cannot block a closed account: " + this.id);
        }

        if(this.status == AccountStatus.ACTIVE) {
            this.status = AccountStatus.BLOCKED;
        }
    }

    public void unlock() {
        if(this.status == AccountStatus.CLOSED) {
            throw new InvalidStatusTransitionException("Cannot unlock a closed account: " + this.id);
        }

        if(this.status == AccountStatus.BLOCKED) {
            this.status = AccountStatus.ACTIVE;
        }
    }

    public void close(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if(this.status != AccountStatus.CLOSED) {
            this.status = AccountStatus.CLOSED;
            this.closedAt = now;
        }
    }

    public AccountId getId() {
        return id;
    }

    public PrincipalId getOwnerId() {
        return ownerId;
    }

    public InstanceCurrency getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
