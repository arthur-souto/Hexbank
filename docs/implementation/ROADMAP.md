# Roadmap de implementação — ordem recomendada

Cada fase depende da anterior; os critérios de pronto são verificáveis. **Não são estimativas de duração.** Em toda fase: atualizar testes, README e ADRs.

## Fase 0 — Fundação e fronteiras
- [ ] Gerar projeto Java 21 + Spring Boot + Gradle.
- [ ] Definir módulos (`accounts`, `ledger`, `transfers`, `funding`, etc.) com camadas hexagonais.
- [ ] Configurar PostgreSQL via Docker Compose, Flyway, perfil test, Testcontainers, JUnit 5, ArchUnit.
- [ ] Criar health endpoint e tratamento padronizado de erro.
- [ ] Implementar identidade simulada só para testes locais, com contrato de principal e autorização desde o início.
- [ ] Formalizar ADR-0001; escrever teste que proíbe dependências ilegais.
**Pronto quando:** aplicação sobe, migration executa, smoke test e teste arquitetural passam.

## Fase 1 — Contas + Ledger + funding fictício
- [ ] Implementar `Money`, Account, LedgerAccount, Journal, JournalEntry e convenção débito/crédito.
- [ ] Projetar schema/constraints e contas de contrapartida.
- [ ] Criar conta, consultar conta/saldo, lançar funding exclusivo de dev/test.
- [ ] Materializar projeção de saldo na mesma transação dos entries.
- [ ] Registrar ADR-0002 e invariantes em testes puros + Postgres.
**Pronto quando:** totais contábeis fecham e projeção coincide com entries; funding tem contrapartida.

## Fase 2 — Transferência interna confiável
- [ ] Criar caso de uso, controller, DTOs, errors e autorização de origem.
- [ ] Implementar idempotência persistente por `(principal, operation, key)` + hash do payload.
- [ ] Garantir atomicidade com ledger e status, e locks ordenados/projeção condicional.
- [ ] Escrever testes com repetição após timeout HTTP, duplicidade concorrente e 50 débitos simultâneos.
- [ ] Comparar pessimista/otimista/UPDATE condicional somente após baseline correto.
**Pronto quando:** sem gasto duplo, sem journal órfão e sem duplicação por replay.

## Fase 3 — Eventos confiáveis
- [ ] Introduzir tabela de Outbox na mesma transação financeira.
- [ ] Escolher RabbitMQ (padrão inicial sugerido) ou Kafka por ADR, não os dois.
- [ ] Publisher com retry/backoff e consumers com inbox/deduplicação.
- [ ] Notificação simulada após `transfer.completed`.
**Pronto quando:** desligar broker não perde evento e duplicidade não causa novo efeito financeiro.

## Fase 4 — Pix simulado + Saga
- [ ] Implementar simulador externo HTTP separado (pode rodar como processo auxiliar **somente aqui** para injetar falhas de rede).
- [ ] Implementar reserva de saldo, envio com ID estável, confirmação, recusa e estado desconhecido.
- [ ] Adicionar job de consulta de pendentes, timeout, retry seguro, backoff/jitter e circuit breaker.
- [ ] Testar falha depois da execução externa e reconciliar divergências.
**Pronto quando:** nenhuma operação externa duplicada e nenhuma reserva perdida/solta duas vezes.

## Fase 5 — Gateway de pagamento simulado
- [ ] Criar autorização, captura, cancelamento e estorno com tokens de cartão fictícios.
- [ ] Modelar transições e idempotência por etapa; gerar eventos e webhooks HMAC.
- [ ] Testar callback repetido, cobrança não duplicada e tentativas de entrega de webhook.
**Pronto quando:** autorização não se confunde com captura e replays são seguros.

## Fase 6 — Reconciliação, defesa e escala
- [ ] Job de reconciliação: ledger vs simulador e saldos projetados.
- [ ] Regras básicas antifraude pré-execução + detecção por eventos.
- [ ] Redis para cache **não autoritativo** do extrato e rate limit (token bucket).
- [ ] Observabilidade progressiva: métricas, tracing, logs correlacionados.
- [ ] k6, 1.000 operações com falhas injetadas, documentar métricas reais e gargalos.
**Pronto quando:** divergências são reportadas, invariantes preservados e resultados reproduzíveis.

## Fase 7 — Estudos de System Design (documentar, não implantar sem motivo)
- [ ] API Gateway externo e critérios de extração de serviços.
- [ ] Projetar extrato com cursor, links de cobrança e Pix em alta escala.
- [ ] Comparar monólito vs serviços separados incluindo perda de transação local.
- [ ] Fazer simulações de entrevista e registrar trade-offs.

## Próxima tarefa concreta
**Implementar SOMENTE a Fase 0.** Nenhum endpoint financeiro antes de congelar a convenção contábil e as regras de idempotência. Não antecipar Redis/Kafka/gateway externo.
