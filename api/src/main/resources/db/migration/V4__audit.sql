-- Trilha de auditoria (BR-050, BR-051, ADR-0004). Espelha shared/domain/AuditEntry.
-- Só identificadores opacos e códigos: nada de nome, documento, e-mail, payload, token,
-- Idempotency-Key ou valores em dinheiro.
CREATE TABLE audit.audit_log (
    id              UUID         PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    principal_id    UUID         NOT NULL,
    action          VARCHAR(32)  NOT NULL CHECK (action IN ('ACCOUNT_CREATED', 'FUNDING_POSTED')),
    aggregate_type  VARCHAR(16)  NOT NULL CHECK (aggregate_type IN ('ACCOUNT', 'JOURNAL')),
    aggregate_id    UUID         NOT NULL,
    -- Mesmo limite do RequestIdFilter ([A-Za-z0-9._:-]{1,128}).
    request_id      VARCHAR(128) NOT NULL CHECK (btrim(request_id) <> ''),
    correlation_id  VARCHAR(128),
    from_status     VARCHAR(16),
    to_status       VARCHAR(16),
    -- Espelha ReasonCode: UPPER_SNAKE_CASE, até 64 caracteres.
    reason          VARCHAR(64)  CHECK (reason ~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$'),
    CONSTRAINT status_pair CHECK ((from_status IS NULL) = (to_status IS NULL))
);
CREATE INDEX idx_audit_log_aggregate ON audit.audit_log (aggregate_type, aggregate_id);
CREATE INDEX idx_audit_log_request ON audit.audit_log (request_id);

-- Append-only (ADR-0004 seção 1): mesma defesa dos journals.
CREATE FUNCTION audit.reject_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% em %.% não é permitido: tabela append-only', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit.audit_log
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit.audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_mutation();
