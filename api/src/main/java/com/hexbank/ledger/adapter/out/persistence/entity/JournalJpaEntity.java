package com.hexbank.ledger.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Immutable
@Table(name = "journals", schema = "ledger")
public class JournalJpaEntity {

    @Id
    private UUID id;

    @Column(name = "reference_type", nullable = false, updatable = false, length = 16)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reversal_of", updatable = false)
    private UUID reversalOf;

    protected JournalJpaEntity() { }

    public JournalJpaEntity(UUID id, String referenceType, UUID referenceId, Instant createdAt, UUID reversalOf) {
        this.id = id;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
        this.reversalOf = reversalOf;
    }
}
