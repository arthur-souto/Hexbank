package com.hexbank.ledger.domain;

import com.hexbank.shared.domain.Money;

import java.time.Instant;
import java.util.*;

public final class Journal {
    private final JournalId journalId;
    private final JournalReferenceType referenceType;
    private final UUID referenceId;
    private final Instant createdAt;
    private final JournalId reversalOf;
    private final List<JournalEntry> entries;

    private final static int MINIMUM_ENTRIES = 2;

    private Journal(JournalId id, JournalReferenceType referenceType, UUID referenceId,
                    Instant createdAt, JournalId reversalOf, List<JournalEntry> entries) {
        this.journalId = Objects.requireNonNull(id, "id cannot be null");
        this.referenceType = Objects.requireNonNull(referenceType, "referenceType cannot be null");
        this.referenceId = Objects.requireNonNull(referenceId, "referenceId cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.reversalOf = reversalOf;
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries cannot be null"));
        validate();
    }

    private void validate() {
        requireAtLeastTwoEntries();
        requireSingleCurrency();
        requireNoRepeatedAccount();
        requireBalancedEntries();
    }

    private void requireAtLeastTwoEntries() {
        if(entries.size() < MINIMUM_ENTRIES) {
            throw new IllegalArgumentException("A journal must have at least two entries");
        }
    }

    private void requireSingleCurrency() {
        final var currency = entries.getFirst().amount().currency();
        boolean multiCurrency = entries.stream()
                .anyMatch(e -> !e.amount().currency().equals(currency));
        if(multiCurrency) {
            throw new IllegalArgumentException("All entries in a journal must have the same currency");
        }

    }

    private void requireNoRepeatedAccount() {
        Set<LedgerAccountId> accountIds = new HashSet<>();

        for(JournalEntry journalEntry : entries) {
            if(!accountIds.add(journalEntry.ledgerAccountId())) {
                throw new IllegalArgumentException("A journal cannot have multiple entries for the same account: " + journalEntry.ledgerAccountId());
            }
        }
    }

    private void requireBalancedEntries() {
        Money totalDebits = sumOf(EntrySide.DEBIT);
        Money totalCredits = sumOf(EntrySide.CREDIT);

        if(!totalDebits.equals(totalCredits)) {
            throw new IllegalArgumentException("Journal entries are not balanced: debits=" + totalDebits + ", credits=" + totalCredits);
        }
    }

    private Money sumOf(EntrySide side) {
        Money zero = new Money(entries.getFirst().amount().currency(), 0);
        return entries.stream()
                .filter(e -> e.side() == side)
                .map(JournalEntry::amount)
                .reduce(zero, Money::plus);
    }

    public static Journal post(JournalId id, JournalReferenceType referenceType, UUID referenceId,
    Instant createdAt, List<JournalEntry> entries) {
        if(referenceType == JournalReferenceType.REVERSAL) {
            throw new IllegalArgumentException("Use reverse() to create a reversal journal");
        }
        return new Journal(id, referenceType, referenceId, createdAt, null, entries);
    }

    public Journal reverse(JournalId newId, Instant now) {
        if (newId.equals(journalId)) {
            throw new IllegalArgumentException("reversal must have a new id");
        }

        if(referenceType == JournalReferenceType.REVERSAL) {
            throw new IllegalStateException("cannot reverse a reversal journal");
        }

        List<JournalEntry> reversedEntries = entries.stream()
                .map(JournalEntry::reversed)
                .toList();

        return new Journal(newId, JournalReferenceType.REVERSAL, journalId.value(), now, journalId, reversedEntries);
    }

    public JournalId getId() { return journalId; }
    public JournalReferenceType getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Optional<JournalId> getReversalOf() { return Optional.ofNullable(reversalOf); }
    public List<JournalEntry> getEntries() { return entries; }

}
