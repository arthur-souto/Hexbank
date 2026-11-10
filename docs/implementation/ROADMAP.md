# Roadmap de implementação — ordem recomendada

Cada fase depende da anterior; os critérios de pronto são verificáveis. **Não são estimativas de duração.** Em toda fase: atualizar testes, README e ADRs.

## Fase 0 — Fundação e fronteiras ✅ concluída (2026-11-03)
- [x] Gerar projeto Java 25 + Spring Boot 4.1 + Maven.
- [x] Definir módulos (`identity`, `accounts`, `ledger`, `transfers`, `funding`, `shared`) com camadas hexagonais (pacotes + `package-info`).
- [x] Configurar PostgreSQL via Docker Compose (com healthcheck), Flyway (`V1__baseline` cria schemas por módulo), profiles `dev`/`test`, Testcontainers, JUnit 5, ArchUnit.
- [x] Health endpoint público e tratamento padronizado de erro (`problem+json` com `code` e `requestId`, inclusive 401/403 do Spring Security).
- [x] `X-Request-Id` ecoado/gerado e propagado ao MDC dos logs.
- [x] Identidade simulada só em `dev`/`test` (`X-Dev-Principal`, `X-Dev-Roles`), `PrincipalId` de domínio, `/v1/dev/**` exige `TEST_ADMIN`; sem profile tudo é 401.
- [x] Swagger/OpenAPI só em `dev`/`test`, com esquema de segurança da identidade simulada.
- [x] ADR-0001 formalizado; `ArchitectureTest` proíbe framework no domínio, ciclos entre módulos, inversão de camadas e acesso fora da API pública de outro módulo.
- [x] CI (GitHub Actions) com `./mvnw clean verify`.

**Pronto quando:** aplicação sobe, migration executa, smoke test e teste arquitetural passam.
**Evidência:** `PhaseZeroAcceptanceTest`, `DefaultProfileSecurityTest`, `ArchitectureTest`, `ApiApplicationTests` (17 testes verdes).

### Pendências conscientes herdadas para a Fase 1
- `ApiApplication` vive em `com.hexbank.bootstrap` com scan explícito de `com.hexbank` (componentes, entidades e repositórios); todo `@SpringBootTest` declara `classes = ApiApplication.class`.
- ~~Pacote `com.hexbank.api` está vazio e não consta no ARCHITECTURE.md: definir propósito ou remover.~~ Resolvido: pacote removido (commit `896d82a`).
- Controller de identidade (`GET /v1/me`) ainda não existe; resolver o principal nos controllers via `@AuthenticationPrincipal PrincipalId`.
- Ainda não há tratamento de `MethodArgumentNotValidException` com lista de campos: adicionar junto do primeiro DTO com Bean Validation (`spring-boot-starter-validation`).

## Fase 1 — Contas + Ledger + funding fictício
- [x] Registrar **ADR-0002 (convenção contábil)** antes do código ([`docs/adr/0002-convencao-contabil.md`](../adr/0002-convencao-contabil.md), status: aceito): natureza das contas (ativo/passivo), sinal de débito/crédito, contas de contrapartida do funding.
- [x] Registrar **ADR-0003 (ciclo de vida e encerramento de contas)** ([`docs/adr/0003-ciclo-de-vida-e-encerramento-de-contas.md`](../adr/0003-ciclo-de-vida-e-encerramento-de-contas.md), status: aceito) e **ADR-0004 (retenção de auditoria)** ([`docs/adr/0004-retencao-de-auditoria.md`](../adr/0004-retencao-de-auditoria.md), status: aceito).
- [x] Implementar `Money` e `Currency` (`InstanceCurrency`) em `shared/domain` (shared kernel) seguindo a [especificação do `Money`](../architecture/ARCHITECTURE.md#money--o-que-deve-fazer-e-respeitar) e a [BR-001](../domain/BUSINESS_RULES.md#dinheiro-e-contas), com testes puros de propriedade; regra ArchUnit: `shared` não depende de módulos.
- Implementar no `domain`, sem Spring/JPA (ArchUnit já bloqueia), seguindo as especificações do `ARCHITECTURE.md` (formato: [como ler as especificações](../architecture/ARCHITECTURE.md#como-ler-as-especificações-de-implementação), [modelo](../implementation/SPEC_TEMPLATE.md)):
  - [x] [Account](../architecture/ARCHITECTURE.md#account--o-que-deve-fazer-e-respeitar) (`AccountTest`)
  - [x] [LedgerAccount](../architecture/ARCHITECTURE.md#ledgeraccount) e [LedgerAccountType/EntrySide](../architecture/ARCHITECTURE.md#ledgeraccounttype-e-entryside) (`LedgerAccountTest`, `LedgerAccountTypeTest`)
  - [x] [JournalEntry](../architecture/ARCHITECTURE.md#journalentry) (`JournalEntryTest`)
  - [x] [Journal](../architecture/ARCHITECTURE.md#journal) (`JournalTest`)
  - [x] [BalanceProjection](../architecture/ARCHITECTURE.md#balanceprojection) (`BalanceProjectionTest`, LED-BP-01 a 08; 09 e 10 são integração, junto com os adapters)
  - [x] [IdempotencyKey](../architecture/ARCHITECTURE.md#idempotencykey-shareddomain) (`IdempotencyKeyTest`, SHR-IK-01 a 03)
  - [x] [AuditEntry](../architecture/ARCHITECTURE.md#auditentry-e-porta-de-auditoria) e `AuditLogPort` (`AuditEntryTest`, SHR-AU-01 e 02 #1 a #4; 02 #5 e 03 são integração, junto com `V4__audit.sql`)
- [ ] Casos de uso (ACC-UC, LED-UC, FUN): [abrir/consultar conta](../architecture/ARCHITECTURE.md#casos-de-uso-de-accounts), [API pública do ledger](../architecture/ARCHITECTURE.md#api-pública-do-ledger-applicationportin) e [funding](../architecture/ARCHITECTURE.md#funding-fictício-post-v1devfunding), respeitando as [dependências entre módulos](../architecture/ARCHITECTURE.md#dependências-entre-módulos-na-fase-1).
- [x] Migrations `V2__accounts.sql`, `V3__ledger.sql` nos schemas `accounts`/`ledger` com CHECKs, FKs e bloqueio de UPDATE/DELETE em entries (trigger). A `V3` cria `ledger_accounts`, `journals`, `journal_entries` e `balance_projections`, com triggers append-only em journals/entries e a projeção zerada do `SIMULATED_CASH:BRL`. Os testes de integração das constraints entram junto com os adapters.
- [ ] Adaptadores `adapter/out/persistence` (JPA ou JDBC) separados das entidades de domínio. Já existe o de `Account`; faltam `LedgerAccount`, `Journal` (gera o `id` de cada entry e preenche o `journal_id`), `BalanceProjection` e `AuditLogPort`.
- [ ] Endpoints `POST /v1/accounts`, `GET /v1/accounts/{id}`, `GET /v1/accounts/{id}/balance` com owner vindo do `PrincipalId`.
- [ ] `POST /v1/dev/funding` com `@Profile({"dev","test"})` no controller **e** `TEST_ADMIN` (já exigido em `SecurityConfig`).
- [ ] Funding exige `Idempotency-Key` desde já (ausente ou fora de 16–128 caracteres → `400 INVALID_REQUEST`), para o contrato HTTP não mudar na Fase 2. **Sem deduplicação na Fase 1**: repetir a chamada gera novo journal (limitação consciente, só `dev`/`test`).
- [ ] Materializar projeção de saldo na mesma transação dos entries.
- [ ] `audit_log` mínimo ([ADR-0004](../adr/0004-retencao-de-auditoria.md)): ~~migration `V4__audit.sql` no schema `audit` com bloqueio de UPDATE/DELETE~~ (feito); registros `ACCOUNT_CREATED` e `FUNDING_POSTED` gravados na mesma transação do caso de uso; teste de integração confirma o registro e a ausência de dados sensíveis.
- [ ] Novos `code`s em `ErrorCodes` ([lista da Fase 1](../architecture/ARCHITECTURE.md#códigos-de-erro-novos-na-fase-1): `ACCOUNT_NOT_FOUND`, `ACCOUNT_NOT_ACTIVE`, `INVALID_STATUS_TRANSITION`, `INSUFFICIENT_FUNDS`) + documentação no Swagger (`@Operation`, `@ApiResponse`).
- [ ] Testes: unitários de domínio, `@WebMvcTest` + `spring-security-test` para contrato HTTP, integração Postgres para invariantes.
**Pronto quando:** totais contábeis fecham e projeção coincide com entries; funding tem contrapartida; criação de conta e funding deixam registro no `audit_log`.

## Fase 2 — Transferência interna confiável
- [ ] Criar caso de uso, controller, DTOs, errors e autorização de origem.
- [ ] Implementar idempotência persistente por `(principal, operation, key)` + hash do payload, aplicada a transferências **e** ao funding (que já recebe a chave desde a Fase 1).
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
**Implementar os adapters de persistência do ledger e da auditoria.** O domínio da Fase 1 está fechado e as migrations `V1`–`V4` estão aplicadas. Agora vêm os adapters de `LedgerAccount`, `Journal` + entries, `BalanceProjection` (gravação condicional por `version`, LED-BP-09) e `AuditLogPort`, com testes de integração para LED-BP-09/10, as `UNIQUE` de `journals` e o bloqueio de `UPDATE`/`DELETE` (SHR-AU-03 #3). Os triggers impedem limpeza por `DELETE`/`TRUNCATE`, então os testes usam `@Transactional` com rollback. Depois, os casos de uso. Deduplicação idempotente fica para a Fase 2 (na Fase 1 o funding só valida a `Idempotency-Key`). Não antecipar Redis/Kafka/gateway externo.
