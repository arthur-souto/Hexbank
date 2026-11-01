# Modelo de dados proposto

**Status:** desenho, não migrações executadas. PostgreSQL único inicialmente. Cada módulo é dono de suas tabelas; acesso cruzado somente via portas públicas. Preferir `UUID`, `TIMESTAMPTZ`, `BIGINT` para unidades menores e constraints explícitas.

## Ownership
| Tabela | Módulo | Colunas essenciais e restrições |
|---|---|---|
| `accounts` | accounts | `id PK`, `owner_id`, `currency`, `status`, `created_at`; index por owner |
| `ledger_accounts` | ledger | `id PK`, `account_id` opcional, `currency`, `type`, `normal_balance`; unique para relação com conta do cliente |
| `journals` | ledger | `id PK`, `reference_type`, `reference_id`, `created_at`, `reversal_of` opcional; unique contra duplicação de journal por referência |
| `journal_entries` | ledger | `id PK`, `journal_id FK`, `ledger_account_id FK`, `side` (DEBIT/CREDIT), `minor_units > 0`, `currency`; imutáveis |
| `balance_projections` | ledger | `ledger_account_id PK`, `booked_minor_units`, `reserved_minor_units`, `version`; atualização transacional |
| `holds` | ledger | `id PK`, `account_id`, `transfer_id UNIQUE`, `amount_minor`, `status`; liberação/consumo único |
| `transfers` | transfers | `id PK`, origem, destino, moeda, valor, status, `journal_id UNIQUE`, timestamps |
| `idempotency_records` | transfers/funding | `(principal_id, operation, key) UNIQUE`, `payload_hash`, `status`, `response_status`, `response_body`, `expires_at` |
| `audit_log` | shared/security | `id PK`, principal, action, aggregate, correlation, timestamp; sem segredos |
| `outbox_events` | outbox (futuro) | `event_id PK`, tipo, versão, aggregate, payload, estado, tentativa, next_attempt_at |
| `inbox_events` | consumidores (futuro) | `(consumer_name, event_id) UNIQUE`, processado em |

## Restrições importantes
1. O Postgres **não consegue** validar `SUM(entries) == 0` entre múltiplas linhas por simples `CHECK`; garantir por API de lançamento transacional e testes, com estratégia adicional de proteção contra escrita por fora.
2. `journal_entries` não deve aceitar UPDATE/DELETE da aplicação; considerar permissões DB e triggers como defesa em profundidade.
3. Usar índice/constraint único na idempotência **além** de checagem na aplicação.
4. As projeções de saldo devem mudar na mesma transação que o journal; aplicar controle de versão/row lock e uma única convenção de sinal.
5. Proteger contra overflow de `BIGINT` no domínio e camada SQL.
6. Nenhuma FK entre módulos deve abrir caminho para manipular tabelas de outro módulo a partir da lógica de negócio; FK de integridade pode ser avaliada e documentada como trade-off no monólito.

## Exemplos de consultas de invariantes (pseudocódigo SQL)
```sql
-- Journals com débitos diferentes dos créditos (uma moeda por journal no MVP)
SELECT journal_id,
       SUM(CASE WHEN side = 'DEBIT' THEN minor_units ELSE -minor_units END) AS delta
FROM journal_entries
GROUP BY journal_id
HAVING SUM(CASE WHEN side = 'DEBIT' THEN minor_units ELSE -minor_units END) <> 0;
```
Retorno esperado: **zero linhas**. Também validar moeda por journal e consistência de projeções em testes próprios.

## Decisões para antes da V1 do schema
Fixar convenção de sinais para ativos/passivos, contas institucionais, retenção de auditoria, exclusão lógica de contas e escopo das reservas. Registrar ADR-0002.
