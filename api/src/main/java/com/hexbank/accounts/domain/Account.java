package com.hexbank.account.domain;

import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.domain.InstanceCurrency;

import java.time.Instant;

public class Account {
    AccountId id;
    PrincipalId ownerId;
    InstanceCurrency currency;
    AccountStatus status;
    Instant createdAt;
    Instant closedAt;

    public Account open(AccountId accountId, PrincipalId ownerId, InstanceCurrency currency) {
        this.id = accountId;
        this.ownerId = ownerId;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now().atZone(java.time.ZoneId.of("UTC")).toInstant();
        return this;
    }

    public void block() {
        if(this.status == AccountStatus.ACTIVE) {
            this.status = AccountStatus.BLOCKED;
        }
    }

    public void unlock() {
        if(this.status == AccountStatus.BLOCKED) {
            this.status = AccountStatus.ACTIVE;
        }
    }

    public void close() {
        if(this.status != AccountStatus.CLOSED) {
            this.status = AccountStatus.CLOSED;
            this.closedAt = Instant.now().atZone(java.time.ZoneId.of("UTC")).toInstant();
        }
    }

    public AccountId getId() {
        return id;
    }

    public void setId(AccountId id) {
        this.id = id;
    }

    public PrincipalId getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(PrincipalId ownerId) {
        this.ownerId = ownerId;
    }

    public InstanceCurrency getCurrency() {
        return currency;
    }

    public void setCurrency(InstanceCurrency currency) {
        this.currency = currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
