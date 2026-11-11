package com.hexbank.ledger.application.port.in;

import com.hexbank.ledger.application.port.in.command.PostJournalCommand;
import com.hexbank.ledger.domain.JournalId;

public interface PostJournalUseCase {
    JournalId post(PostJournalCommand command);
}
