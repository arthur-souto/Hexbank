package com.hexbank.shared.application.port.out;

import com.hexbank.shared.domain.AuditEntry;

public interface AuditLogPort {
    void record(AuditEntry auditEntry);
}
