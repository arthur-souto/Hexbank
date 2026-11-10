package com.hexbank.ledger.adapter.out.persistence.jpa;

import com.hexbank.ledger.adapter.out.persistence.entity.JournalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JournalJpaRepository extends JpaRepository<JournalJpaEntity, UUID> {

}
