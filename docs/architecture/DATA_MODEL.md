# Modelo de dados proposto

**Status:** Fase 1 aplicada pelas migrations `V1`–`V4` (`accounts`, `ledger_accounts`, `journals`, `journal_entries`, `balance_projections`, `audit_log`); as demais tabelas continuam como desenho. PostgreSQL único inicialmente. Cada módulo é dono de suas tabelas; acesso cruzado somente via portas públicas. Preferir `UUID`, `TIMESTAMPTZ`, `BIGINT` para unidades menores e constraints explícitas.

## Ownership
| Tabela | Módulo | Colunas essenciais e restrições |
|---|---|---|
| `accounts` | accounts | `id PK`, `owner_id`, `currency`, `status`, `created_at`; index por owner |
| `ledger_accounts` | ledger | `id PK`, `account_id` opcional (UNIQUE), `code` opcional (UNIQUE, contas institucionais), `currency`, `type`, `normal_balance` (coluna gerada a partir de `type`, nunca gravada pela aplicação); `CHECK` exatamente um entre `account_id` e `code` ([ADR-0002](../adr/0002-convencao-contabil.md)) |
| `journals` | ledger | `id PK`, `reference_type`, `reference_id`, `created_at`, `reversal_of` opcional; unique contra duplicação de journal por referência |
| `journal_entries` | ledger | `id PK`, `journal_id FK`, `ledger_account_id FK`, `side` (DEBIT/CREDIT), `minor_units > 0`, `currency`; imutáveis |
| `balance_projections` | ledger | `ledger_account_id PK`, `booked_minor_units`, `reserved_minor_units`, `version`; atualização transacional |
| `holds` | ledger | `id PK`, `account_id`, `transfer_id UNIQUE`, `amount_minor`, `status`; liberação/consumo único |
| `transfers` | transfers | `id PK`, origem, destino, moeda, valor, status, `journal_id UNIQUE`, timestamps |
| `idempotency_records` | transfers/funding | `(principal_id, operation, key) UNIQUE`, `payload_hash`, `status`, `response_status`, `response_body`, `expires_at` |
| `audit_log` | shared/security (schema `audit`) | `id PK`, `occurred_at`, `principal_id`, `action`, `aggregate_type`/`aggregate_id`, `request_id`/`correlation_id`, `from_status`/`to_status` e `reason` opcionais; append-only, sem segredos nem dados pessoais ([ADR-0004](../adr/0004-retencao-de-auditoria.md)) |
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
- **Decidido no [ADR-0002](../adr/0002-convencao-contabil.md)** (status: aceito): convenção de sinais para ativos/passivos, contas institucionais (`SIMULATED_CASH:BRL`) e escopo das reservas (hold não gera journal no MVP).
- **Decidido no [ADR-0003](../adr/0003-ciclo-de-vida-e-encerramento-de-contas.md)** (status: aceito): exclusão lógica/encerramento de contas (BR-005). `CLOSED` é terminal, exige saldo zero e nenhum hold ativo; `V2__accounts.sql` tem `CHECK` de status e `closed_at`.
- **Decidido no [ADR-0004](../adr/0004-retencao-de-auditoria.md)** (status: aceito): `audit_log` append-only, retenção indefinida no MVP, só identificadores opacos.
