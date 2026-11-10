package com.hexbank.accounts.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(schema = "accounts", name = "accounts")
public class AccountJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    public AccountJpaEntity update(String status, Instant closedAt) {
        this.status = status;
        this.closedAt = closedAt;
        return this;
    }

    protected AccountJpaEntity() { }

    public AccountJpaEntity(UUID id, UUID ownerId, String currency, String status, Instant createdAt, Instant closedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
    }
}

