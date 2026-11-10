package com.hexbank.ledger.adapter.out.persistence;

import com.hexbank.ledger.adapter.out.persistence.entity.JournalEntryJpaEntity;
import com.hexbank.ledger.adapter.out.persistence.entity.JournalJpaEntity;
import com.hexbank.ledger.adapter.out.persistence.jpa.JournalEntryJpaRepository;
import com.hexbank.ledger.adapter.out.persistence.jpa.JournalJpaRepository;
import com.hexbank.ledger.application.port.out.JournalRepository;
import com.hexbank.ledger.domain.Journal;
import com.hexbank.ledger.domain.JournalId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JournalPersistenceAdapter implements JournalRepository {

    private final JournalJpaRepository journalJpaRepository;
    private final JournalEntryJpaRepository journalEntryJpaRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Journal journal) {
        UUID journalId = journal.getId().value();

        JournalJpaEntity entity = new JournalJpaEntity(
                journalId,
                journal.getReferenceType().name(),
                journal.getReferenceId(),
                journal.getCreatedAt(),
                journal.getReversalOf().map(JournalId::value).orElse(null)
        );

        journalJpaRepository.save(entity);

        journalEntryJpaRepository.saveAll(journal.getEntries().stream()
                .map(entry -> new JournalEntryJpaEntity(
                        UUID.randomUUID(),
                        journalId,
                        entry.ledgerAccountId().value(),
                        entry.side().name(),
                        entry.amount().minorUnits(),
                        entry.amount().currency().name()
                ))
                .toList()
        );
    }
}
