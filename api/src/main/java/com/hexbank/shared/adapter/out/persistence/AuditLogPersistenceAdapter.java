package com.hexbank.shared.adapter.out.persistence;

import com.hexbank.shared.adapter.out.persistence.entity.AuditLogJpaEntity;
import com.hexbank.shared.adapter.out.persistence.jpa.AuditLogJpaRepository;
import com.hexbank.shared.application.port.out.AuditLogPort;
import com.hexbank.shared.domain.AuditEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditLogPersistenceAdapter implements AuditLogPort {

    private final AuditLogJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry auditEntry) {
        repository.save(new AuditLogJpaEntity(
                auditEntry.id(), auditEntry.occurredAt(), auditEntry.principalId(),
                auditEntry.action().name(), auditEntry.aggregateType().name(), auditEntry.aggregateId(),
                auditEntry.requestId(), auditEntry.correlationId(),
                auditEntry.fromStatus(), auditEntry.toStatus(), auditEntry.reason()
        ));
    }
}
