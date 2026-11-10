package com.hexbank.ledger.application.port.out;

import com.hexbank.ledger.domain.Journal;

public interface JournalRepository {
    void save(Journal journal);
}
