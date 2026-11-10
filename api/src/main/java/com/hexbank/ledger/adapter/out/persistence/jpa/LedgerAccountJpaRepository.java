package com.hexbank.ledger.adapter.out.persistence.jpa;

import com.hexbank.ledger.adapter.out.persistence.entity.LedgerAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountJpaRepository extends JpaRepository<LedgerAccountJpaEntity, UUID> {
    Optional<LedgerAccountJpaEntity> findByAccountId(UUID accountId);
    Optional<LedgerAccountJpaEntity> findByCode(String code);
}
