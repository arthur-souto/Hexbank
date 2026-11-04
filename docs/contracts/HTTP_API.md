# Contratos HTTP — versão planejada v1

**Status:** convenções transversais **implementadas na Fase 0** (autenticação simulada, `X-Request-Id`, formato de erro). Endpoints de domínio ainda **PROPOSTOS**. Base `/v1`. JSON UTF-8. Datas RFC 3339 em UTC. IDs UUID. `Money` representado como `{ "currency": "BRL", "minorUnits": 10000 }` (R$ 100,00). Não aceitar float para valores.

OpenAPI gerado pelo springdoc em `dev`/`test`: `/v3/api-docs` (JSON) e `/swagger-ui.html` (só `dev`). Desligado sem profile.

## Implementado na Fase 0
| Item | Comportamento |
|---|---|
| `GET /actuator/health` | público, `200 {"status":"UP"}` |
| Autenticação simulada (`dev`/`test`) | `X-Dev-Principal: <uuid>` autentica; `X-Dev-Roles: A,B` vira `ROLE_A`, `ROLE_B`. Ausente ou UUID inválido = anônimo |
| Sem profile `dev`/`test` | filtro não existe; toda rota exceto health retorna 401 |
| `/v1/dev/**` | exige `ROLE_TEST_ADMIN`, senão 403 `FORBIDDEN` |
| Demais rotas | exigem autenticação, senão 401 `UNAUTHENTICATED` |
| Rota inexistente (autenticado) | 404 `NOT_FOUND` |
| `X-Request-Id` | aceito se `[A-Za-z0-9._:-]{1,128}`, senão substituído por UUID; sempre devolvido e presente no log (MDC) e no corpo de erro |

## Convenções
- `Authorization: Bearer <token>` obrigatório quando autenticação real (JWT) estiver habilitada; até lá, só a identidade simulada acima, restrita a `dev`/`test`.
- `X-Request-Id` opcional do cliente; servidor gera se ausente ou inválido, ecoa no retorno.
- `Idempotency-Key` string opaca obrigatória em POST financeiro; tamanho de 16 a 128 caracteres (proposta).
- Resposta de erro usa `application/problem+json` com `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId`.
- Paginação de extrato será por cursor opaco. Não confiar em campos `ownerId` enviados pelo cliente.
- Replays idempotentes retornam o mesmo status/corpo da primeira conclusão bem-sucedida ou recusa determinística, e cabeçalho `Idempotent-Replay: true`.

## Criar conta (fase 1)
`POST /v1/accounts` — `201 Created`; owner vem do principal.
```json
{"currency":"BRL"}
```
```json
{"id":"a5420e4b-1092-4eac-bedd-21e774c4bc5e","currency":"BRL","status":"ACTIVE"}
```

## Consultar conta e saldo (fase 1)
`GET /v1/accounts/{id}` — dados acessíveis ao principal.
`GET /v1/accounts/{id}/balance` — `200 OK`:
```json
{"accountId":"a5420e4b-1092-4eac-bedd-21e774c4bc5e","currency":"BRL","bookedMinorUnits":10000,"reservedMinorUnits":2000,"availableMinorUnits":8000,"asOf":"2026-11-01T20:00:00Z"}
```

## Funding de laboratório (fase 1; apenas `dev` / `test`)
`POST /v1/dev/funding` — necessita principal administrativo de teste e `Idempotency-Key`; **desabilitado em qualquer outro profile**:
```json
{"accountId":"a5420e4b-1092-4eac-bedd-21e774c4bc5e","amount":{"currency":"BRL","minorUnits":10000}}
```
O endpoint gera journal balanceado com contrapartida institucional; não é depósito real.

**Idempotência por fase:**
- **Fase 1:** `Idempotency-Key` obrigatória e validada (ausente ou fora de 16–128 caracteres → `400 INVALID_REQUEST`), mas **sem deduplicação**: repetir a requisição gera novo funding.
- **Fase 2:** deduplicação persistente (BR-030 a BR-033), com o mesmo contrato; replays retornam `Idempotent-Replay: true`.

Conta destino fora de `ACTIVE` → `409 ACCOUNT_NOT_ACTIVE` ([ADR-0003](../adr/0003-ciclo-de-vida-e-encerramento-de-contas.md)).

## Transferência interna (fase 2)
`POST /v1/transfers` — requer `Idempotency-Key`; `201 Created`.
```json
{
  "fromAccountId":"a5420e4b-1092-4eac-bedd-21e774c4bc5e",
  "toAccountId":"98615277-a068-4508-b423-bd9aa73f68ea",
  "amount":{"currency":"BRL","minorUnits":4000}
}
```
```json
{
  "id":"d3ad411e-6a8c-4696-9f42-0c347712d4f4",
  "status":"COMPLETED",
  "fromAccountId":"a5420e4b-1092-4eac-bedd-21e774c4bc5e",
  "toAccountId":"98615277-a068-4508-b423-bd9aa73f68ea",
  "amount":{"currency":"BRL","minorUnits":4000},
  "createdAt":"2026-11-01T20:00:00Z"
}
```
`GET /v1/transfers/{id}` — consulta limitada às partes autorizadas.

## Erros estáveis
| HTTP | `code` | Causa |
|---|---|---|
| 400 | `INVALID_REQUEST` | contrato ou valor inválido |
| 401 | `UNAUTHENTICATED` | falta autenticação |
| 403 | `FORBIDDEN` | sem permissão |
| 404 | `NOT_FOUND` | rota/recurso genérico inexistente (Fase 0) |
| 405 | `METHOD_NOT_ALLOWED` | método HTTP não suportado (Fase 0) |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` não suportado (Fase 0) |
| 404 | `ACCOUNT_NOT_FOUND` | conta inexistente ou ocultada por política de privacidade |
| 409 | `INSUFFICIENT_FUNDS` | saldo disponível insuficiente |
| 409 | `ACCOUNT_NOT_ACTIVE` | conta não pode movimentar |
| 409 | `IDEMPOTENCY_CONFLICT` | mesma chave, payload distinto |
| 409 | `OPERATION_IN_PROGRESS` | mesma operação ainda em processamento |
| 429 | `RATE_LIMITED` | quota excedida (fase posterior) |
| 500 | `INTERNAL_ERROR` | falha não especificada, sem stack trace exposto |
| 503 | `DEPENDENCY_UNAVAILABLE` | dependência indisponível (futuro) |

Exemplo:
```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Saldo disponível insuficiente","instance":"/v1/transfers","code":"INSUFFICIENT_FUNDS","requestId":"req-123"}
```

## Endpoints futuros (não implementados)
- `POST /v1/pix/transfers` -> `202 Accepted` + status `PENDING`, ver docs de eventos.
- `GET /v1/pix/transfers/{id}` -> estado externo conhecido.
- `POST /v1/card-payments/authorizations`, `/captures`, `/refunds` -> somente tokens simulados.
- `POST /v1/merchant-webhooks/test` -> teste de assinatura, não callback real.

## Contratos que precisam de revisão antes de publicar
Política de replays de erro 5xx, vencimento de chave, paginação de extrato, rotatividade de token e permissões. Alterações quebradoras criam `/v2` ou migração compatível documentada.
