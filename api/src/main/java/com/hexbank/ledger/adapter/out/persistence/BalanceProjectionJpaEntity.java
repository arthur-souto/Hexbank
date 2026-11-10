package com.hexbank.ledger.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Entity
@Getter
@Table(name = "balance_projections", schema = "ledger")
public class BalanceProjectionJpaEntity {

    @Id
    @Column(name = "ledger_account_id")
    private UUID ledgerAccountId;

    @Column(name = "booked_minor_units", nullable = false)
    private long bookedMinorUnits;

    @Column(name = "reserved_minor_units", nullable = false)
    private long reservedMinorUnits;

    @Version
    private long version;

    protected BalanceProjectionJpaEntity() { }

    BalanceProjectionJpaEntity(UUID ledgerAccountId, long bookedMinorUnits, long reservedMinorUnits) {
        this.ledgerAccountId = ledgerAccountId;
        this.bookedMinorUnits = bookedMinorUnits;
        this.reservedMinorUnits = reservedMinorUnits;
    }

    BalanceProjectionJpaEntity update(long bookedMinorUnits, long reservedMinorUnits) {
        this.bookedMinorUnits = bookedMinorUnits;
        this.reservedMinorUnits = reservedMinorUnits;
        return this;
    }
}
