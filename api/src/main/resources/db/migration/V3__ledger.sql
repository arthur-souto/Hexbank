-- Contas contábeis do ledger (ADR-0002).
-- Cada linha é OU conta de cliente (account_id) OU conta institucional (code), nunca as duas.
CREATE TABLE ledger.ledger_accounts (
    id              UUID        PRIMARY KEY,
    account_id      UUID        UNIQUE,
    code            VARCHAR(64) UNIQUE,
    currency        VARCHAR(3)  NOT NULL CHECK (currency IN ('BRL')),
    type            VARCHAR(16) NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY')),
    -- Derivado do type pelo próprio banco (espelha LedgerAccountType.normalBalance()); a aplicação nunca grava.
    -- Sem ELSE de propósito: um tipo novo sem mapeamento vira NULL e o NOT NULL rejeita a linha.
    normal_balance  VARCHAR(6)  NOT NULL GENERATED ALWAYS AS (
        CASE type
            WHEN 'ASSET'     THEN 'DEBIT'
            WHEN 'LIABILITY' THEN 'CREDIT'
            WHEN 'EQUITY'    THEN 'CREDIT'
        END
    ) STORED,
    CONSTRAINT exactly_one_bond CHECK ((account_id IS NULL) <> (code IS NULL)),
    CONSTRAINT customer_account_is_liability CHECK (account_id IS NULL OR type = 'LIABILITY'),
    -- Alvo da FK composta de journal_entries: garante no banco que a moeda do entry = moeda da conta.
    CONSTRAINT uq_ledger_accounts_id_currency UNIQUE (id, currency)
);

-- Journals: cabeçalho do lançamento (BR-010, BR-011, ADR-0002 seção 4).
-- Append-only: nunca UPDATE/DELETE (trigger abaixo). Estorno = novo journal com reversal_of.
CREATE TABLE ledger.journals (
    id              UUID        PRIMARY KEY,
    reference_type  VARCHAR(16) NOT NULL CHECK (reference_type IN ('FUNDING', 'TRANSFER', 'REVERSAL')),
    reference_id    UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    reversal_of     UUID        REFERENCES ledger.journals (id),
    -- Um journal por referência de negócio (LED-UC-02 #7).
    CONSTRAINT uq_journals_reference UNIQUE (reference_type, reference_id),
    -- No máximo um estorno por original (ADR-0002).
    CONSTRAINT uq_journals_reversal_of UNIQUE (reversal_of),
    -- Espelha Journal.post/reverse: só REVERSAL tem reversal_of, e aponta para o próprio reference_id.
    CONSTRAINT reversal_matches_type CHECK ((reference_type = 'REVERSAL') = (reversal_of IS NOT NULL)),
    CONSTRAINT reversal_references_original CHECK (reversal_of IS NULL OR reference_id = reversal_of),
    CONSTRAINT reversal_not_self CHECK (reversal_of IS NULL OR reversal_of <> id)
);

-- Linhas do lançamento. O id é da persistência; o domínio não tem id por entry.
-- Soma de débitos = soma de créditos não cabe em CHECK (várias linhas): garantida pelo Journal e por testes.
CREATE TABLE ledger.journal_entries (
    id                 UUID        PRIMARY KEY,
    journal_id         UUID        NOT NULL REFERENCES ledger.journals (id),
    ledger_account_id  UUID        NOT NULL,
    side               VARCHAR(6)  NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    minor_units        BIGINT      NOT NULL CHECK (minor_units > 0),
    currency           VARCHAR(3)  NOT NULL CHECK (currency IN ('BRL')),
    CONSTRAINT fk_journal_entries_account_currency
        FOREIGN KEY (ledger_account_id, currency) REFERENCES ledger.ledger_accounts (id, currency),
    -- Espelha Journal.requireNoRepeatedAccount.
    CONSTRAINT uq_journal_entries_account_per_journal UNIQUE (journal_id, ledger_account_id)
);
CREATE INDEX idx_journal_entries_ledger_account ON ledger.journal_entries (ledger_account_id);

-- Projeção de saldo (ADR-0002 seções 5 e 6). Muda na mesma transação do journal, com version (LED-BP-09).
-- A moeda vem da ledger_account. "Cliente nunca negativo" (BR-016) depende do tipo da conta,
-- que está em outra tabela; fica no domínio (BalanceProjection.apply).
CREATE TABLE ledger.balance_projections (
    ledger_account_id     UUID   PRIMARY KEY REFERENCES ledger.ledger_accounts (id),
    booked_minor_units    BIGINT NOT NULL,
    reserved_minor_units  BIGINT NOT NULL CHECK (reserved_minor_units >= 0),
    version               BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

-- Defesa em profundidade (BR-011, DATA_MODEL restrição 2): journals e entries são imutáveis.
CREATE FUNCTION ledger.reject_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% em %.% não é permitido: tabela append-only', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER journals_append_only
    BEFORE UPDATE OR DELETE ON ledger.journals
    FOR EACH ROW EXECUTE FUNCTION ledger.reject_mutation();
CREATE TRIGGER journals_no_truncate
    BEFORE TRUNCATE ON ledger.journals
    FOR EACH STATEMENT EXECUTE FUNCTION ledger.reject_mutation();

CREATE TRIGGER journal_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger.journal_entries
    FOR EACH ROW EXECUTE FUNCTION ledger.reject_mutation();
CREATE TRIGGER journal_entries_no_truncate
    BEFORE TRUNCATE ON ledger.journal_entries
    FOR EACH STATEMENT EXECUTE FUNCTION ledger.reject_mutation();

-- Caixa simulado: contrapartida do funding fictício (ADR-0002, seção 3).
-- UUID fixo para ser o mesmo em todos os ambientes; não alterar.
INSERT INTO ledger.ledger_accounts (id, account_id, code, currency, type)
VALUES ('00000000-0000-0000-0000-00000000ca5e', NULL, 'SIMULATED_CASH:BRL', 'BRL', 'ASSET');

-- Projeção zerada do caixa: LED-UC-02 trava as projeções com SELECT ... FOR UPDATE, então ela precisa existir.
INSERT INTO ledger.balance_projections (ledger_account_id, booked_minor_units, reserved_minor_units, version)
VALUES ('00000000-0000-0000-0000-00000000ca5e', 0, 0, 0);
