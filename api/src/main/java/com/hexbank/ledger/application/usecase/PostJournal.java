package com.hexbank.ledger.application.usecase;

import com.hexbank.ledger.application.port.in.PostJournalUseCase;
import com.hexbank.ledger.application.port.in.command.PostJournalCommand;
import com.hexbank.ledger.application.port.out.BalanceProjectionRepository;
import com.hexbank.ledger.application.port.out.JournalRepository;
import com.hexbank.ledger.application.port.out.LedgerAccountRepository;
import com.hexbank.ledger.domain.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

public class PostJournal implements PostJournalUseCase {

    private final Clock clock;
    private final JournalRepository journalRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final BalanceProjectionRepository balanceProjectionRepository;

    public PostJournal(Clock clock,
                       JournalRepository journalRepository,
                       LedgerAccountRepository ledgerAccountRepository,
                       BalanceProjectionRepository balanceProjectionRepository) {
        this.clock = clock;
        this.journalRepository = journalRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.balanceProjectionRepository = balanceProjectionRepository;
    }


    private List<BalanceProjection> getBalanceProjection(List<LedgerAccount> accounts) {
        List<BalanceProjection> balanceProjections = balanceProjectionRepository.lockForUpdate(accounts);

        if(balanceProjections.size() < accounts.size()) {
            Set<LedgerAccountId> foundIds = balanceProjections.stream()
                    .map(BalanceProjection::getLedgerAccountId)
                    .collect(Collectors.toSet());

            Set<LedgerAccountId> missingIds = accounts.stream()
                    .map(LedgerAccount::getId)
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());

            throw new IllegalStateException(
                    "Balance projections not found for ledger accounts: " + missingIds
            );
        }

        return balanceProjections;
    }

    private List<LedgerAccount> getAccounts(Journal journal) {

        Set<LedgerAccountId> distinctIds = journal.getEntries()
                .stream()
                .map(JournalEntry::ledgerAccountId)
                .collect(Collectors.toSet());

        List<LedgerAccount> accounts = ledgerAccountRepository.findAllById(distinctIds);

        if(accounts.size() < distinctIds.size()) {
            Set<LedgerAccountId> foundIds = accounts.stream()
                    .map(LedgerAccount::getId)
                    .collect(Collectors.toSet());

            Set<LedgerAccountId> missingIds = new HashSet<>(distinctIds);
            missingIds.removeAll(foundIds);

            throw new IllegalArgumentException(
                    "One or more ledger accounts were not found: " + missingIds
            );
        }

        return accounts;
    }

    private List<BalanceProjection> updateJournal(List<LedgerAccount> accounts, List<BalanceProjection> balanceProjections, Journal journal) {
        Map<LedgerAccountId, LedgerAccount> accountsById = accounts.stream()
                .collect(Collectors.toMap(LedgerAccount::getId, a -> a));

        Map<LedgerAccountId, BalanceProjection> projectionsById = balanceProjections.stream()
                .collect(Collectors.toMap(BalanceProjection::getLedgerAccountId, p -> p));

        List<BalanceProjection> updated = new ArrayList<>();

        for(JournalEntry entry : journal.getEntries()) {
            LedgerAccount account = accountsById.get(entry.ledgerAccountId());
            BalanceProjection balanceProjection = projectionsById.get(entry.ledgerAccountId());
            updated.add(balanceProjection.apply(entry, account));
        }

        return updated;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public JournalId post(PostJournalCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        JournalId journalId = new JournalId(UUID.randomUUID());

        List<JournalEntry> journalEntries = command.entries()
                .stream().map(c -> new JournalEntry(
                        c.ledgerAccountId(),
                        c.side(),
                        c.amount()
                ))
                .toList();

        Journal journal = Journal.post(
                journalId,
                command.referenceType(),
                command.referenceId(),
                clock.instant(),
                journalEntries
        );

        List<LedgerAccount> accounts = getAccounts(journal);
        List<BalanceProjection> balanceProjections = getBalanceProjection(accounts);
        List<BalanceProjection> updated = updateJournal(accounts, balanceProjections, journal);

        journalRepository.save(journal);
        updated.forEach(balanceProjectionRepository::update);

        return journalId;
    }
}
