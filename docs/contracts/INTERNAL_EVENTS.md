# Contratos internos e eventos

## Portas de aplicação planejadas (assinaturas conceituais)
```java
interface OpenAccountUseCase { AccountResult open(OpenAccountCommand command, PrincipalId actor); }
interface GetBalanceUseCase { BalanceResult get(AccountId id, PrincipalId actor); }
interface PostJournalUseCase { JournalId post(BalancedJournal journal, CorrelationId correlationId); }
interface InitiateTransferUseCase {
    TransferResult initiate(TransferCommand command, IdempotencyKey key, PrincipalId actor);
}
```
O exemplo não define classes compiláveis; é um **contrato de responsabilidade**. Cada módulo detém sua API pública. `Transfer` chama interface do `Ledger` **em memória**, não `LedgerHttpAdapter`.

## Operação transacional
Para transferência local, `InitiateTransferUseCase` executa sob transação DB única. Implementações de ledger e idempotência participam da **mesma** transação. Não adicionar transações independentes nos adaptadores por conveniência.

## Eventos de domínio vs integração
Evento de domínio pode permanecer interno e síncrono. Evento de integração é persistido na Outbox e enviado após commit. Não publicar no broker antes de confirmar no Postgres.

Envelope de evento futuro:
```json
{
  "eventId":"7d5fd869-751f-4d8c-b88f-32be34e244b7",
  "eventType":"transfer.completed",
  "eventVersion":1,
  "occurredAt":"2026-11-01T20:00:00Z",
  "aggregateId":"d3ad411e-6a8c-4696-9f42-0c347712d4f4",
  "correlationId":"req-123",
  "payload":{"transferId":"d3ad411e-6a8c-4696-9f42-0c347712d4f4","currency":"BRL","minorUnits":4000}
}
```

| Evento | Produtor | Consumidor futuro | Semântica |
|---|---|---|---|
| `transfer.completed` v1 | transfers | notifications, reconciliation | transferência local confirmada |
| `pix.submitted` v1 | transfers | reconciliation | pedido aceito pelo simulador, não liquidação |
| `pix.confirmed` v1 | transfers | notifications, reconciliation | liquidação simulada confirmada |
| `pix.rejected` v1 | transfers | notifications | recusa confirmada |
| `payment.captured` v1 | payments | webhooks, reconciliation | captura no simulador |

## Garantias
- **At least once**, nunca presumir exactly-once entre broker e consumidores.
- Inbox/deduplicação com `eventId` único por consumidor.
- Ordenação global não garantida; se necessária, chave de partição por `aggregateId` e versões monotônicas.
- Retry com backoff e jitter, DLQ para falhas persistentes; supervisão e replay seguros.
- Dados de eventos mínimos, sem segredos/credenciais/dados sensíveis.

## Webhook futuro ao lojista
Cabeçalhos `X-Hexbank-Event-Id`, `X-Hexbank-Timestamp`, `X-Hexbank-Signature` (HMAC SHA-256 do timestamp + corpo bruto, com segredo por lojista). Verificar assinatura em comparação constant-time, janela de tempo e replays; entrega no mínimo uma vez, com política explícita de retentativa.
