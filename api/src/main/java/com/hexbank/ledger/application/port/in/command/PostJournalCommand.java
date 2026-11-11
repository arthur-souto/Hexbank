package com.hexbank.ledger.application.port.in.command;

import com.hexbank.ledger.domain.JournalReferenceType;

import java.util.List;
import java.util.UUID;

public record PostJournalCommand(
        JournalReferenceType referenceType,
        UUID referenceId,
        List<EntryCommand> entries
) {

}
