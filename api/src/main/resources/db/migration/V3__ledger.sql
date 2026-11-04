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
    CONSTRAINT customer_account_is_liability CHECK (account_id IS NULL OR type = 'LIABILITY')
);

-- Caixa simulado: contrapartida do funding fictício (ADR-0002, seção 3).
-- UUID fixo para ser o mesmo em todos os ambientes; não alterar.
INSERT INTO ledger.ledger_accounts (id, account_id, code, currency, type)
VALUES ('00000000-0000-0000-0000-00000000ca5e', NULL, 'SIMULATED_CASH:BRL', 'BRL', 'ASSET');
