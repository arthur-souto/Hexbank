# Regras de negócio e invariantes

Os IDs `BR-*` são compromissos testáveis. Os valores de limite/configuração de produto são **hipóteses do laboratório**, não exigências bancárias legais.

## Dinheiro e contas
- **BR-001** `Money(currency, minorUnits)` não aceita moeda desconhecida, overflow silencioso ou representação `double`; transferências só entre contas da mesma moeda na fase inicial.
- **BR-002** Conta tem `id` imutável, `ownerId`, `currency`, `status ∈ {ACTIVE, BLOCKED, CLOSED}`; só ACTIVE pode iniciar transferência.
- **BR-003** Contas de clientes não nascem com dinheiro. Funding simulado movimenta contrapartida contábil institucional (proibido em ambiente não-dev).
- **BR-004** Usuário só consulta/opera contas autorizadas; identidade do titular não é decidida exclusivamente pelo payload.
- **BR-005** Conta fechada não realiza operações e deve possuir saldo/pendências conciliadas antes de encerramento.

## Ledger
- **BR-010** Cada `journal` confirmado possui >= 2 entries, débitos e créditos equilibrados por moeda; cada entry pertence a uma conta contábil.
- **BR-011** Entries e journals confirmados são append-only; estorno/ajuste via journal novo referenciando o original, no máximo um estorno lógico por comando de estorno.
- **BR-012** Journals e efeitos de negócio são persistidos atomicamente; IDs externos/correlation IDs não são reutilizados em lançamentos distintos.
- **BR-013** O saldo contábil é derivado de entries ou de projeção mantida transacionalmente; nunca atualizado sem lançamento correspondente.
- **BR-014** Saldo disponível = saldo contábil elegível - reservas ativas - bloqueios aplicáveis. Reservas devem ser contabilizadas/registradas de modo auditável e sua liberação é idempotente.
- **BR-015** Para cada moeda, soma algébrica de entradas no **universo contábil completo** é zero; entradas/saídas externas usam contas de contrapartida. Não usar a soma das contas dos clientes como prova única.
- **BR-016** Nenhuma operação pode tornar saldo disponível negativo, salvo conta institucional explicitamente autorizada.

## Transferências internas
- **BR-020** `amount > 0`; origem e destino diferentes, existentes, ativos e na mesma moeda.
- **BR-021** Transferência interna concluída debita origem e credita destino em **uma única transação**. Não existe sucesso parcial.
- **BR-022** Concorrência de débitos é serializada por bloqueio/atualização condicional adequada; bloqueios de múltiplas contas seguem ordenação estável por ID.
- **BR-023** Transferência tem ID único, estado `COMPLETED` no fluxo síncrono do MVP; operações recusadas não produzem journal de movimentação.
- **BR-024** Não fazer retry de operação não idempotente sem chave e sem estado persistido.

## Idempotência
- **BR-030** `Idempotency-Key` obrigatória para operações que movem dinheiro; escopo `(principal, operation, key)` com unique index no banco.
- **BR-031** Primeira requisição registra hash canônico do payload e seu estado; repetição com chave+payload iguais obtém mesmo resultado de negócio (sem novo journal).
- **BR-032** Mesma chave + payload diferente retorna `409 IDEMPOTENCY_CONFLICT`.
- **BR-033** Repetições concorrentes não duplicam efeitos; quando execução original ainda está em curso, responder `409 OPERATION_IN_PROGRESS` com `Retry-After`, ou aguardar de forma limitada — estratégia fixa por contrato.
- **BR-034** A semântica de retenção da chave é parte do contrato: **mínimo de 48h** para laboratório (decisão proposta, configurável); após expiração, não reutilizar chaves para tentar repetir operações históricas. Transações também possuem ID de negócio único persistente.

## Integrações posteriores
- **BR-040** Pix simulado: `CREATED -> RESERVED -> SUBMITTED -> {CONFIRMED, REJECTED, UNKNOWN}`; UNKNOWN evolui por consulta para CONFIRMED ou REJECTED; não presumir falha por timeout.
- **BR-041** Envio externo usa identificador de operação estável e semântica de idempotência suportada pelo simulador; reenvio só quando comprovadamente seguro.
- **BR-042** Confirmação liquida reserva exatamente uma vez; recusa libera reserva exatamente uma vez; casos inconclusivos permanecem para reconciliação.
- **BR-043** Pagamento com cartão simulado distingue `AUTHORIZED`, `CAPTURED`, `VOIDED`, `REFUNDED`, e não assume autorização = liquidação.
- **BR-044** Não coletar ou persistir PAN/CVV no backend de estudos: usar exclusivamente token fictício do simulador.
- **BR-045** Webhook pode ser entregue mais de uma vez; assinar payload e timestamp com HMAC, verificar validade temporal e usar `eventId` para deduplicar.
- **BR-046** Antifraude com efeito bloqueante precisa ocorrer **antes** da execução; consumo assíncrono posterior serve para monitoramento e investigação.

## Auditoria e estados
- **BR-050** Operação auditada com principal, request/correlation ID, instante UTC, identificadores, origem da ação e mudança de status; não registrar credenciais, tokens ou dados sensíveis em logs.
- **BR-051** Mudanças de estado devem ser validadas por transições permitidas; duplicidade de callback não repete efeitos.
- **BR-052** Números financeiros são sempre serializados em unidade menor com moeda explícita; interpretação decimal na UI fica por conta do cliente.

## Exemplo contábil
Funding simulado R$ 100: débito em conta de contrapartida institucional + crédito na conta do cliente, obedecendo à convenção de sinal adotada. Transferência R$ 40 entre clientes: dois lançamentos opostos e balanceados. **A convenção de débito/crédito de contas patrimoniais e os sinais da projeção de saldo devem ser fixados em testes antes da primeira migration.**

## Riscos/decisões em aberto
- Política exata de disponibilidade de reservas e bloqueios.
- Prazo de retenção legal não se aplica automaticamente ao laboratório; 48h é apenas hipótese para chave de API, **não** para journals.
- Modelo de cobrança/taxas e multimoeda estão fora da fase inicial.
