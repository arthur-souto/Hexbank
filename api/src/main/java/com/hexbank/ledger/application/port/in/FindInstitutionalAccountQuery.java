package com.hexbank.ledger.application.port.in;

import com.hexbank.ledger.domain.LedgerAccountId;

public interface FindInstitutionalAccountQuery {
    LedgerAccountId byCode(String code);
}
