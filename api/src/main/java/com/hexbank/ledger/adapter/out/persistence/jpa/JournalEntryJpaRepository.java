package com.hexbank.ledger.adapter.out.persistence.jpa;

import com.hexbank.ledger.adapter.out.persistence.entity.JournalEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalEntryJpaRepository extends JpaRepository<JournalEntryJpaEntity, UUID> {
    List<JournalEntryJpaEntity> findByJournalId(UUID journalId);
    List<JournalEntryJpaEntity> findByLedgerAccountId(UUID ledgerAccountId);
}
