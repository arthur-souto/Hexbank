# ADR-0004 — Retenção e conteúdo do log de auditoria

- **Status:** aceito (2026-11-04)
- **Data:** 2026-11-04
- **Relacionado a:** BR-034, BR-050, BR-051, `docs/operations/SECURITY.md` (regras 8 e 10), `docs/architecture/DATA_MODEL.md` (`audit_log`), [ADR-0002](0002-convencao-contabil.md), [ADR-0003](0003-ciclo-de-vida-e-encerramento-de-contas.md)

## Contexto

A BR-050 exige que operações sejam auditadas (principal, request/correlation ID, instante UTC, identificadores,
origem e mudança de status) sem credenciais, tokens ou dados sensíveis. O `DATA_MODEL.md` prevê a tabela
`audit_log` no módulo `shared/security`. O `BUSINESS_RULES.md` registra que prazos de retenção legal **não** se
aplicam automaticamente ao laboratório e que as 48h da BR-034 valem só para chaves de idempotência.

Faltava definir:

1. Por quanto tempo os registros ficam guardados.
2. Se o `audit_log` pode ser alterado ou apagado.
3. Que dados podem (e não podem) entrar no registro.
4. Como ele se relaciona com o ledger e com os logs da aplicação.

## Decisão

### 1. Append-only

O `audit_log` é **append-only**, como os journals (BR-011):

- A aplicação só faz `INSERT`; nunca `UPDATE` ou `DELETE`.
- Defesa em profundidade igual à de `journal_entries`: trigger que rejeita `UPDATE`/`DELETE` e/ou usuário de
  banco da aplicação sem essas permissões.
- Correção de um registro errado = novo registro referenciando o anterior, nunca edição.

### 2. Retenção indefinida no MVP

- Registros são mantidos **indefinidamente** no laboratório. Não há exigência legal real, e o volume do MVP é
  pequeno.
- Expurgo ou arquivamento (ex.: particionar por mês e mover partições antigas) fica **fora do MVP**. Quando
  entrar, será um job explícito, documentado e ele mesmo auditado; nunca um `DELETE` ad hoc.
- A retenção do `audit_log` é independente:
  - das chaves de idempotência (BR-034, mínimo 48h, podem expirar);
  - dos journals/entries (permanentes, ADR-0002);
  - dos logs de aplicação em stdout (efêmeros, sem garantia de retenção).

### 3. Conteúdo: só identificadores opacos

Cada registro contém:

| Campo | Exemplo | Observação |
|---|---|---|
| `id` | UUID | PK |
| `occurred_at` | `TIMESTAMPTZ` UTC | BR-050 |
| `principal_id` | UUID do `PrincipalId` | quem agiu |
| `action` | `ACCOUNT_CREATED`, `FUNDING_POSTED`, `ACCOUNT_STATUS_CHANGED` | vocabulário fechado, `CHECK` ou enum no domínio |
| `aggregate_type` / `aggregate_id` | `ACCOUNT` / UUID | sobre o que agiu |
| `request_id` / `correlation_id` | `X-Request-Id` | rastreio com logs |
| `from_status` / `to_status` | `ACTIVE` → `CLOSED` | opcional; mudanças de estado (BR-051, ADR-0003) |
| `reason` | `CUSTOMER_REQUEST` | opcional; código, não texto livre |

**Proibido** no `audit_log` (SECURITY.md regras 8 e 10):

- nome, e-mail, documento, telefone ou qualquer dado pessoal identificável em texto;
- credenciais, tokens, headers de autenticação, `Idempotency-Key`;
- payload bruto da requisição ou resposta;
- texto livre digitado pelo usuário.

Valores financeiros também **não** são copiados para a auditoria: o registro aponta para o `journal_id`/`transfer_id`,
e o valor é lido no ledger. Assim existe uma única fonte de verdade para dinheiro.

Como só há UUIDs, encerrar uma conta (ADR-0003) ou anonimizar um titular no futuro não exige reescrever o log: basta
que o vínculo UUID → pessoa deixe de existir no módulo `identity`.

### 4. Papel do `audit_log`

- **Não** é fonte de verdade de saldo nem de estado; ledger e tabelas de domínio são.
- Responde "quem fez o quê, quando e a partir de qual requisição".
- Recusas de negócio relevantes (ex.: tentativa de encerrar conta com saldo, acesso negado a conta alheia) também
  são auditadas, sem os dados da requisição.

## Alternativas consideradas

1. **Retenção curta (ex.: 90 dias) com expurgo automático.** Mais próxima de sistemas reais com custo de
   armazenamento, mas exige job e política antes de existir volume. Descartada no MVP.
2. **Auditoria só nos logs de aplicação.** Simples, mas sem garantia de retenção, sem consulta estruturada e sem
   proteção contra alteração. Descartada.
3. **Guardar payload completo para "investigação".** Facilita debug, mas viola SECURITY.md e cria um repositório de
   dados sensíveis. Descartada.
4. **Auditoria permitindo `UPDATE` para correções.** Destrói o valor probatório do log. Descartada.
5. **Event sourcing como auditoria.** O ledger já é append-only para dinheiro; replicar para todo o domínio
   está fora do objetivo do laboratório. Descartada.

## Consequências

- ✅ Coerente com o ledger: nada que importe para reconstituir a história é apagado ou editado.
- ✅ Sem dados pessoais no log, a LGPD afeta só o módulo `identity`, não a auditoria.
- ✅ Correlação direta com logs via `request_id`.
- ⚠️ A tabela cresce sem limite; aceitável no laboratório, exige revisão antes de testes de carga (Fase 6).
- ⚠️ Investigar um incidente exige juntar `audit_log` + ledger + logs; não há "foto completa" num só lugar.
- ⚠️ O vocabulário de `action` precisa ser mantido junto com os casos de uso.

## Fora do escopo deste ADR

Migração da escrita da auditoria para Outbox (avaliar na Fase 3; na Fase 1 ela é gravada na mesma transação
do caso de uso, ver `ROADMAP.md`), expurgo/arquivamento, acesso de leitura à auditoria (endpoint ou só SQL) e retenção de dados pessoais do módulo
`identity`.

## Quando revisar

- Testes de carga (Fase 6) mostrarem crescimento relevante da tabela.
- Introdução de dados pessoais reais ou de autenticação real (JWT).
- Necessidade de endpoint de consulta de auditoria.
- Requisito regulatório simulado de prazo mínimo/máximo.
