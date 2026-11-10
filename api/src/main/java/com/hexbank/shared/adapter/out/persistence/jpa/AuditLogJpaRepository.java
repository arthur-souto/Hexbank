package com.hexbank.shared.adapter.out.persistence.jpa;

import com.hexbank.shared.adapter.out.persistence.entity.AuditLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, UUID> {
}
