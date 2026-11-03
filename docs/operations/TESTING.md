# Estratégia de testes e falhas

## Pirâmide
- **Domínio:** unitários puros para `Money`, estados, journal balanceado, regras de fundos.
- **Aplicação:** fakes de portas, autorização, idempotência, sequência de estados.
- **Integração:** Testcontainers PostgreSQL para transações, migrations, locks, constraints, concorrência.
- **Arquitetura:** ArchUnit para evitar imports ilegais, ciclos e vazamento JPA para domínio.
- **Contrato:** HTTP (`MockMvc` ou similar) e eventos versionados.
- **Sistema:** k6, cenários de falha e reconciliadores em Docker Compose quando existentes.

## Testes existentes (Fase 0)
| Classe | Tipo | Cobre |
|---|---|---|
| `ArchitectureTest` | ArchUnit | domínio sem Spring/JPA/Hibernate/Lombok; módulos sem ciclos; `adapter -> application -> domain`; outro módulo só via `application.port.in` ou `domain` |
| `PhaseZeroAcceptanceTest` | integração (Testcontainers, profile `test`) | migrations criam schemas; health público; 401/403/404 em `problem+json`; principal inválido = anônimo; `X-Request-Id`; OpenAPI publicado |
| `DefaultProfileSecurityTest` | integração (sem profile) | header dev ignorado; Swagger desligado; health funciona |
| `ApiApplicationTests` | smoke | contexto sobe |

Infra de teste: `TestContainersConfiguration` (Postgres 16 via `@ServiceConnection`) e `TestApiApplication` (`./mvnw spring-boot:test-run`). Todo teste de integração exige Docker ligado.

Convenções para próximas fases: domínio em testes puros sem Spring; contrato HTTP com `@WebMvcTest` + `spring-security-test`; invariantes contábeis e concorrência sempre contra Postgres real (nunca H2).

## Aceitação mínima por fase
| Fase | Teste obrigatório |
|---|---|
| 0 | boot + migrations + regra de dependências passam |
| 1 | journal não balanceado rejeitado; funding fictício cria contrapartida; projections batem com entries |
| 2 | 10 POSTs com mesma chave geram 1 transferência; chave+payload diferente = 409; 50 débitos concorrentes não geram negativo |
| 3 | broker desligado e religado: outbox entrega; duplicidade não duplica efeitos |
| 4 | timeout após execução externa: nunca duplica Pix; status UNKNOWN recuperado; reserva termina corretamente |
| 5 | captura/estorno não duplica; webhook assinado e repetido não altera estado indevidamente |
| 6 | reconciliação identifica discrepâncias; métricas mostram erros; testes de carga documentados |

## Matriz de falhas
| Cenário | Esperado |
|---|---|
| Exceção antes do commit interno | rollback total de transferência e journal |
| Processo cai após commit antes da resposta | cliente repete chave e obtém resultado original |
| 50 operações concorrem por R$ 100 | saldo nunca negativo; apenas operações cobertas por fundos concluem |
| Transferências simultâneas A→B e B→A | nenhum deadlock permanente; retry delimitado se necessário |
| Broker fora do ar | journals persistem; outbox pendente entrega ao restaurar |
| Simulador executa mas retorna timeout | estado desconhecido; consulta antes de compensar/repetir |
| Callback externo duplicado/fora de ordem | transição inválida ignorada/rejeitada e auditada |
| Webhook consumidor offline | reentrega com assinatura e mesmo eventId |

## Registros de experimentos
Criar `docs/experiments/YYYY-MM-DD-nome.md` com hipótese, versão de código, ambiente, volume, passos, resultados, p95/p99, violações do invariante, logs sanitizados e conclusão. Não preencher métricas fictícias.
