package com.hexbank.shared.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Immutable
@Table(name = "audit_log", schema = "audit")
public class AuditLogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "principal_id", nullable = false, updatable = false)
    private UUID principalId;

    @Column(nullable = false, updatable = false, length = 32)
    private String action;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 16)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "request_id", nullable = false, updatable = false, length = 128)
    private String requestId;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @Column(name = "from_status", updatable = false, length = 16)
    private String fromStatus;

    @Column(name = "to_status", updatable = false, length = 16)
    private String toStatus;

    @Column(updatable = false, length = 64)
    private String reason;

    protected AuditLogJpaEntity() { }

    AuditLogJpaEntity(UUID id, Instant occurredAt, UUID principalId, String action, String aggregateType,
                      UUID aggregateId, String requestId, String correlationId,
                      String fromStatus, String toStatus, String reason) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.principalId = principalId;
        this.action = action;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }
}
