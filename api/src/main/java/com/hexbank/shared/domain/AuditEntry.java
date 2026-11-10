package com.hexbank.shared.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


public record AuditEntry(
        UUID id,
        Instant occurredAt,
        UUID principalId,
        AuditAction action,
        AuditAggregateType aggregateType,
        UUID aggregateId,
        String requestId,
        String correlationId,
        String fromStatus,
        String toStatus,
        String reason
) {
    public AuditEntry {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(requestId, "request id cannot be null");
        if(requestId.isBlank()) {
            throw new IllegalArgumentException("request id cannot be blank");
        }
        Objects.requireNonNull(principalId, "principal id cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(aggregateType, "aggregate type cannot be null");
        Objects.requireNonNull(aggregateId, "aggregateId cannot be null");

        if((fromStatus == null) != (toStatus == null)) {
            throw new IllegalArgumentException("fromStatus and toStatus must be both set or both null");
        }

        if(reason != null) {
            ReasonCode.requireValid(reason);
        }
    }
}
