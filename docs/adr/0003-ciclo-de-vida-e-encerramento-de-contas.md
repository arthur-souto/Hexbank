# ADR-0003 — Ciclo de vida e encerramento de contas

- **Status:** aceito (2026-11-04)
- **Data:** 2026-11-04
- **Relacionado a:** BR-002, BR-005, BR-014, BR-016, BR-020, BR-022, BR-050, BR-051, [ADR-0002](0002-convencao-contabil.md), [ADR-0004](0004-retencao-de-auditoria.md)

## Contexto

A BR-002 fixa `status ∈ {ACTIVE, BLOCKED, CLOSED}` e diz que só `ACTIVE` inicia transferência. A BR-005 diz que
conta fechada não opera e precisa estar conciliada antes do encerramento. Faltava definir:

1. Quais transições de status são permitidas.
2. O que "saldo/pendências conciliadas" significa de forma verificável.
3. Se contas `BLOCKED`/`CLOSED` podem **receber** valores (a BR-002 só fala em iniciar).
4. Quem pode bloquear, desbloquear e encerrar.
5. O que acontece com os dados de uma conta encerrada.

Uma conta **nunca pode ser apagada**: os `journal_entries` imutáveis (BR-011) apontam para a `ledger_account`
dela (ADR-0002). "Excluir" aqui sempre significa mudança de status.

## Decisão

### 1. Máquina de estados

```
          bloquear (admin)
 ACTIVE  ─────────────────▶  BLOCKED
    ▲  ◀─────────────────      │
    │     desbloquear (admin)  │
    │                          │
    └──── encerrar ──▶ CLOSED ◀┘ encerrar
```

| De | Para | Quem | Pré-condição |
|---|---|---|---|
| `ACTIVE` | `BLOCKED` | admin | — |
| `BLOCKED` | `ACTIVE` | admin | — |
| `ACTIVE` | `CLOSED` | titular ou admin | conta conciliada (item 2) |
| `BLOCKED` | `CLOSED` | admin | conta conciliada (item 2) |
| `CLOSED` | qualquer | — | **proibido**: `CLOSED` é terminal |

- Conta nasce `ACTIVE` (`POST /v1/accounts`).
- Transição para o mesmo status é idempotente (não falha, não gera novo registro de auditoria).
- Qualquer outra transição é recusada com `409 INVALID_STATUS_TRANSITION` (BR-051).
- No laboratório, "admin" é o papel `TEST_ADMIN`; o titular de uma conta `BLOCKED` não pode encerrá-la sozinho,
  para que o bloqueio não seja contornado.

### 2. Conta conciliada = pronta para encerrar

Encerrar exige, **na mesma transação** que muda o status:

- `booked_minor_units = 0` na projeção da `ledger_account` da conta;
- `reserved_minor_units = 0` **e** nenhum registro em `ledger.holds` com status ativo.

Se não for o caso, o encerramento é recusado:

| Situação | Erro |
|---|---|
| saldo contábil ≠ 0 | `409 ACCOUNT_HAS_BALANCE` |
| reserva/hold ativo | `409 ACCOUNT_HAS_ACTIVE_HOLDS` |

O sistema **não** move o saldo automaticamente: o titular transfere antes. Isso evita journals sem comando
explícito do cliente e mantém BR-013 (saldo só muda por lançamento com origem clara).

**Concorrência:** o encerramento usa o mesmo bloqueio de linha/atualização condicional dos débitos (BR-022). Uma
transferência e um encerramento simultâneos nunca terminam com conta `CLOSED` e saldo ≠ 0.

### 3. Efeito do status nas operações

| Operação | `ACTIVE` | `BLOCKED` | `CLOSED` |
|---|---|---|---|
| Ser **origem** de transferência | ✅ | ❌ | ❌ |
| Ser **destino** de transferência | ✅ | ❌ | ❌ |
| Receber funding (`dev`/`test`) | ✅ | ❌ | ❌ |
| Consultar conta, saldo e extrato (titular) | ✅ | ✅ | ✅ |

Origem ou destino fora de `ACTIVE` é recusado com `409 ACCOUNT_NOT_ACTIVE`, coerente com a BR-020 ("destino [...]
ativo"). Nenhuma recusa gera journal (BR-023).

Consulta continua permitida em qualquer status: o titular tem direito ao histórico, e o ledger nunca é apagado.

### 4. Persistência

Em `V2__accounts.sql`:

- `status` com `CHECK (status IN ('ACTIVE','BLOCKED','CLOSED'))`.
- `closed_at TIMESTAMPTZ NULL` com `CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))`.
- Nenhum `DELETE` em `accounts.accounts` nem em `ledger.ledger_accounts` pela aplicação.

A `ledger_account` não tem status próprio: o status vive só em `accounts` (um único lugar de verdade).

**Direção da dependência (emenda de 2026-11-04):** `accounts → ledger`, nunca o contrário. A abertura de conta cria a
`ledger_account` e o encerramento consulta o saldo pela porta pública do ledger; se o ledger também consultasse
`accounts`, haveria ciclo entre módulos (reprovado pelo `ArchitectureTest`). Por isso **quem verifica o status é o
caso de uso que orquestra o lançamento** (`funding`, depois `transfers`), via porta pública de `accounts`, na mesma
transação do lançamento. O ledger não conhece status de conta.

Toda mudança de status gera registro de auditoria com `from_status`/`to_status`, ator e motivo (BR-050, ADR-0004).

### 5. Escopo por fase

- **Fase 1:** `CHECK` e `closed_at` na `V2`; conta nasce `ACTIVE`; funding recusa conta não `ACTIVE`.
  Não há endpoint de bloqueio/encerramento.
- **Fase 2:** transferência aplica a tabela do item 3.
- **Endpoints de bloqueio, desbloqueio e encerramento:** entram quando forem priorizados no roadmap, com contrato
  em `docs/contracts/HTTP_API.md`. As regras deste ADR já valem para o domínio desde a Fase 1.

## Alternativas consideradas

1. **DELETE físico da conta.** Impossível sem apagar ou órfãos journals imutáveis. Descartada.
2. **Encerrar com saldo, movendo-o para uma conta institucional.** Exige definir para onde vai o dinheiro de um
   cliente (conta de "valores a devolver"), novo tipo contábil e um journal sem comando do cliente. Descartada no
   MVP; revisar se houver encerramento forçado por admin.
3. **`CLOSED` reversível (reabrir conta).** Complica auditoria e invariantes ("conta fechada não opera" deixaria
   de ser permanente). Para voltar a operar, o cliente abre uma conta nova. Descartada.
4. **`BLOCKED` aceita crédito, só bloqueia débito.** Comum em bancos reais (bloqueio cautelar), mas abre a dúvida
   de como devolver valores recebidos por engano. Descartada no MVP pela simplicidade; revisar com política de
   bloqueios judiciais.
5. **Status também na `ledger_account`.** Duplicaria a verdade entre módulos e poderia divergir. Descartada.

## Consequências

- ✅ Toda conta encerrada tem saldo zero por construção; o extrato histórico continua íntegro.
- ✅ Recusas são determinísticas e mapeadas para códigos estáveis.
- ✅ Schema da `V2` já nasce com os três status, sem migração futura de enum.
- ⚠️ O cliente precisa esvaziar a conta antes de encerrar (passo extra de UX).
- ⚠️ Todo caso de uso que lança no ledger (`funding`, `transfers`) precisa checar o status em `accounts` antes;
  a checagem precisa estar na mesma transação para não haver corrida.
- ⚠️ Novos códigos de erro: `ACCOUNT_NOT_ACTIVE`, `ACCOUNT_HAS_BALANCE`, `ACCOUNT_HAS_ACTIVE_HOLDS`,
  `INVALID_STATUS_TRANSITION` (documentar em `HTTP_API.md` quando expostos).

## Fora do escopo deste ADR

Bloqueio judicial parcial (valor bloqueado em vez de conta inteira), encerramento forçado com saldo, prazos de
inatividade e retenção de dados pessoais do titular após encerramento.

## Quando revisar

- Bloqueio parcial ou judicial (`bloqueios aplicáveis` da BR-014).
- Encerramento por iniciativa do banco com saldo remanescente.
- Requisito de reabertura de conta.
- Liquidação externa (Fase 4) com valores em trânsito para conta que será encerrada.
