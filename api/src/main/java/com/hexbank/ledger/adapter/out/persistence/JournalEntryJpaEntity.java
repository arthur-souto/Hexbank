package com.hexbank.ledger.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

@Entity
@Getter
@Immutable
@Table(name = "journal_entries", schema = "ledger")
public class JournalEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private UUID journalId;

    @Column(name = "ledger_account_id", nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Column(nullable = false, updatable = false, length = 6)
    private String side;

    @Column(name = "minor_units", nullable = false, updatable = false)
    private long minorUnits;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    protected JournalEntryJpaEntity() { }

    JournalEntryJpaEntity(UUID id, UUID journalId, UUID ledgerAccountId, String side, long minorUnits, String currency) {
        this.id = id;
        this.journalId = journalId;
        this.ledgerAccountId = ledgerAccountId;
        this.side = side;
        this.minorUnits = minorUnits;
        this.currency = currency;
    }
}
