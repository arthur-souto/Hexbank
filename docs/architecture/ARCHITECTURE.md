# Arquitetura — monólito modular hexagonal

## Decisão
Um **deploy Spring Boot** e **um PostgreSQL**, com schemas ou ownership de tabelas por módulo. Sem HTTP interno entre módulos. Infraestrutura do monólito não é sinônimo de ausência de fronteiras.

## Módulos e responsabilidades
| Módulo | Responsabilidade | Quando |
|---|---|---|
| `identity` | principal autenticado e autorização (mock controlado inicial; real antes de expor API) | fase 0/1 |
| `accounts` | ciclo de vida e ownership de conta, consulta de extrato via contrato do ledger | fase 1 |
| `ledger` | journals imutáveis, saldo contábil, invariantes, reserva/projeções | fase 1 |
| `transfers` | orquestração de transferências internas e idempotência | fase 2 |
| `funding` | crédito fictício de testes por contrapartida contábil; só perfil dev/test | fase 1 |
| `outbox` | persistência e publicação confiável de eventos | fase 3 |
| `pix-simulator` | dispositivo externo simulado, via adaptador, falhas controladas | fase 4 |
| `payments` | autorização/captura/estorno simulado de cartão tokenizado | fase 5 |
| `notifications`, `fraud`, `reconciliation` | projeções, sinais e confrontação de dados | fases 3–6 |

## Camadas por módulo
```text
com.hexbank
  accounts/
    domain/                   # entidades, objetos de valor, regras puras
    application/
      port/in/                # casos de uso
      port/out/               # interfaces para persistência/outros módulos
      service/                # orquestração
    adapter/in/http/          # REST e validações de transporte
    adapter/out/persistence/  # JPA/JDBC/Postgres
    config/                   # composition root local
  ledger/...
  transfers/...
  shared/
    domain/                   # shared kernel mínimo: Money, InstanceCurrency
    adapter/in/http/          # tipos técnicos comuns (erro problem+json, request id)
  bootstrap/                  # aplicação e wiring
```

**Regra de dependência:** `adapter -> application -> domain`. Domínio não conhece framework. Cada módulo publica pequena API Java explícita (`port/in` ou facade pública). **Não** importar JPA entities, repositórios ou tabelas privadas de outro módulo. Proibir ciclos com ArchUnit; Spring Modulith pode complementar.

## Shared kernel (`shared/domain`)
Tipos de domínio usados por vários módulos e sem dono natural. Hoje: **`Money` e `InstanceCurrency`** (enum com as moedas suportadas; só `BRL` no MVP).

- **Por que não no `ledger`:** `accounts`, `transfers` e `funding` também usam `Money`. Com o tipo no ledger, `accounts → ledger` somado a `ledger → accounts` (consulta de status, [ADR-0003](../adr/0003-ciclo-de-vida-e-encerramento-de-contas.md)) cria ciclo entre módulos.
- **Entra:** objetos de valor imutáveis, estáveis e sem regra específica de um módulo.
- **Não entra:** IDs de módulos (`AccountId`, `JournalId`, `TransferId`), regras de saldo (BR-016 é do ledger), entidades, portas ou qualquer dependência de framework.
- **Regra de dependência:** `shared` não depende de nenhum módulo (`accounts`, `ledger`, ...). Verificado por `ArchitectureTest.sharedDoesNotDependOnModules`, junto da regra que mantém `..domain..` livre de framework.

### `Money` — o que deve fazer e respeitar
Regras de negócio: **[BR-001](../domain/BUSINESS_RULES.md#dinheiro-e-contas)** (moeda conhecida, sem overflow silencioso, sem `double`) e **[BR-052](../domain/BUSINESS_RULES.md#auditoria-e-estados)** (sempre unidade menor + moeda explícita). Uso no ledger: [ADR-0002](../adr/0002-convencao-contabil.md).

| Regra | Comportamento esperado |
|---|---|
| Representação | `Currency` + `long minorUnits` (R$ 100,00 = `BRL` / `10000`). Nunca `double`/`float`; `BigDecimal` só na borda, se necessário |
| Moeda | apenas moedas suportadas (`BRL` no MVP); moeda nula ou desconhecida é rejeitada na criação |
| Imutável | operações devolvem novo `Money`; igualdade por valor (moeda + `minorUnits`) |
| Mesma moeda | somar, subtrair ou comparar moedas diferentes lança erro, nunca converte |
| Overflow | `Math.addExact`/`subtractExact`: overflow é exceção, nunca valor errado |
| Sinal | `Money` pode ser zero ou negativo como resultado de cálculo; **quem exige positivo é o chamador** (ex.: `JournalEntry` exige `> 0`, ADR-0002; transferência exige `amount > 0`, BR-020) |
| Sem arredondamento | não há divisão nem porcentagem no MVP; quando houver (taxas), entra por ADR com regra de arredondamento explícita |
| Serialização | API usa `{"currency":"BRL","minorUnits":10000}` ([HTTP_API.md](../contracts/HTTP_API.md)); a conversão para decimal é da UI |
| Domínio puro | sem Spring, JPA ou Jackson dentro do tipo; mapeamento fica nos adapters |

## Fronteiras transacionais
- Transferência interna: uma transação ACID PostgreSQL cobre verificação/autorização, reserva ou débito, journal balanceado, transferência, idempotência e eventual outbox quando usada.
- Aplicação não chama API HTTP externa dentro de transação longa.
- Eventos assíncronos posteriores à persistência exigem **Outbox na mesma transação**; publicação via polling e retentativas, consumo idempotente.
- Evitar `REQUIRES_NEW` acidental separando lançamentos da operação; padronizar transações nas application services.

## Fluxos
```mermaid
sequenceDiagram
 participant C as Cliente
 participant H as HTTP
 participant T as Transfers
 participant L as Ledger
 participant D as PostgreSQL
 C->>H: POST /v1/transfers + Idempotency-Key
 H->>T: initiate(principal, command, key)
 T->>D: BEGIN + claim key + lock accounts
 T->>L: post balanced journal
 L->>D: immutable entries
 T->>D: transfer completed + response + COMMIT
 T-->>H: transfer result
 H-->>C: 201 or replay
```

## Evolução
1. Camada HTTP incorporada ao monólito.
2. Se necessário, adicionar reverse proxy/API Gateway externo para roteamento, autenticação de borda e rate limit. **Controles de autorização continuam em cada backend**.
3. Extrair serviço só com necessidade comprovada (deploy, escala, ownership). Antes: contrato estável, sem acesso cruzado a tabelas, eventos versionados, observabilidade e testes de contrato.
4. Ao extrair ledger/transfers, a atomicidade local deixa de existir: redesenhar fluxo transacional, não simplesmente trocar interface Java por HTTP.

## Não objetivos iniciais
Kubernetes, service discovery, service mesh, saga interna desnecessária, Kafka só por moda, Redis como fonte de saldo.
