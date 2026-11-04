package com.hexbank.ledger.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Entity
@Getter
@Table(name = "ledger_accounts", schema = "ledger")
public class LedgerAccountJpaEntity {

    @Id
    private UUID id;

    @Column(unique = true, name = "account_id", updatable = false)
    private UUID accountId;

    @Column(unique = true, updatable = false)
    private String code;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String type;

    protected LedgerAccountJpaEntity() { }

    LedgerAccountJpaEntity(UUID id, UUID accountId, String code, String currency, String type) {
        this.id = id;
        this.accountId = accountId;
        this.code = code;
        this.currency = currency;
        this.type = type;
    }
}
