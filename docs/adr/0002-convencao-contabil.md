# ADR-0002 — Convenção contábil do ledger

- **Status:** aceito (2026-11-04)
- **Data:** 2026-11-04
- **Relacionado a:** BR-010 a BR-016, BR-003, BR-050 (exemplo contábil), `docs/architecture/DATA_MODEL.md`

## Contexto

O ledger é a fonte de verdade do saldo (BR-013). Antes da primeira migration financeira (`V3__ledger.sql`)
precisamos fixar **uma única** convenção de débito/crédito, porque mudá-la depois exige migrar journals
imutáveis. O `DATA_MODEL.md` já antecipa `journal_entries.side` (DEBIT/CREDIT), `minor_units > 0` e
`ledger_accounts.normal_balance`, e o `BUSINESS_RULES.md` descreve o funding como "débito na
contrapartida institucional + crédito na conta do cliente", mas deixa a convenção em aberto.

Perguntas que este ADR responde:
1. Como um lançamento é representado (lado + valor positivo, ou valor com sinal)?
2. Qual a natureza de cada conta (ativo, passivo, patrimônio) e o que débito/crédito faz com ela?
3. Qual é a contrapartida do funding fictício?
4. Como o saldo e a projeção são calculados e verificados?
5. Onde ficam reservas (holds) e estornos?

## Decisão

### 1. Partidas dobradas com lado explícito e valor sempre positivo

Cada `JournalEntry` tem `side ∈ {DEBIT, CREDIT}` e `Money` com `minorUnits > 0`. Não existe valor negativo
em entry. O sinal só aparece no **cálculo** do saldo, a partir da natureza da conta.

### 2. Natureza das contas, do ponto de vista do HEX BANK

| Tipo (`LedgerAccountType`) | Saldo normal (`normal_balance`) | Débito | Crédito | Uso no MVP |
|---|---|---|---|---|
| `ASSET` (ativo) | DEBIT | aumenta | diminui | caixa simulado (contrapartida do funding) |
| `LIABILITY` (passivo) | CREDIT | diminui | aumenta | **contas de clientes**: dinheiro que o banco deve ao cliente |
| `EQUITY` (patrimônio) | CREDIT | diminui | aumenta | reservado; sem conta no MVP |

`INCOME` e `EXPENSE` (taxas, juros) ficam fora do MVP e entram por ADR próprio quando houver cobrança.

`normal_balance` é **derivado** do tipo no domínio (`type.normalBalance()`); a coluna existe no banco
para leitura e checagem por SQL, com `CHECK` coerente com o tipo.

### 3. Contas institucionais e contrapartida do funding

| Conta | Tipo | Moeda | Papel |
|---|---|---|---|
| `SIMULATED_CASH` | ASSET | uma por moeda (BRL no MVP) | origem do dinheiro fictício do funding |

- Criada pela migration (`V3__ledger.sql`) com **UUID fixo** e `code` único (`SIMULATED_CASH:BRL`),
  sem vínculo com `accounts`.
- Só o módulo `funding`, apenas em `dev`/`test`, gera lançamentos contra ela (BR-003).
- Contas futuras (ex.: liquidação Pix na Fase 4) seguem o mesmo padrão e são registradas em ADR próprio.

Cada conta de cliente (`accounts.accounts`) tem **exatamente uma** `ledger_account` do tipo `LIABILITY`,
na mesma moeda (`UNIQUE(account_id)`).

### 4. Lançamentos de referência

**Funding de R$ 100,00 (10000 minor units)**: caixa do banco sobe, dívida com o cliente sobe.

| Conta | Lado | Valor |
|---|---|---|
| `SIMULATED_CASH:BRL` (ASSET) | DEBIT | 10000 |
| cliente A (LIABILITY) | CREDIT | 10000 |

**Transferência de R$ 40,00 de A para B**: a dívida com A cai, a dívida com B sobe.

| Conta | Lado | Valor |
|---|---|---|
| cliente A (LIABILITY) | DEBIT | 4000 |
| cliente B (LIABILITY) | CREDIT | 4000 |

**Estorno de um journal**: novo journal com `reversal_of = <original>`, mesmas contas e valores, lados
invertidos. No máximo um estorno por journal original (`UNIQUE(reversal_of)`). Nunca UPDATE/DELETE (BR-011).

### 5. Saldo e projeção

Saldo de uma conta, **na direção do seu saldo normal**:

```
booked = Σ entries no lado normal − Σ entries no lado oposto
```

- Cliente (LIABILITY): `booked = créditos − débitos`. Depois do exemplo acima: A = 6000, B = 4000.
- `SIMULATED_CASH` (ASSET): `booked = débitos − créditos` = 10000.

Assim, um saldo positivo sempre significa "a conta tem valor no seu sentido natural", e o cliente
enxerga saldo positivo sem inverter sinais na API (BR-052).

`balance_projections.booked_minor_units` guarda esse mesmo número, atualizado **na mesma transação** do
journal (BR-013) por `booked += delta`, onde `delta = +valor` se o entry está no lado normal da conta e
`−valor` caso contrário. Somas usam `Math.addExact` (overflow vira erro, nunca valor errado).

`available = booked − reserved_minor_units` (BR-014). Para contas `LIABILITY` de cliente, nenhum journal
pode deixar `available < 0` (BR-016). `SIMULATED_CASH` não tem esse limite.

### 6. Invariantes verificáveis

| Invariante | Onde é garantido | Como é verificado |
|---|---|---|
| Journal tem ≥ 2 entries e Σ débitos = Σ créditos, por moeda (BR-010) | construtor de `Journal` no domínio | teste unitário + consulta SQL do `DATA_MODEL.md` retorna 0 linhas |
| Entry na mesma moeda da sua conta; journal com uma única moeda no MVP | domínio + FK/consulta | teste unitário |
| Universo fecha: Σ débitos = Σ créditos de **todos** os entries, por moeda (BR-015) | consequência do anterior | consulta global no teste de integração |
| Equação contábil: Σ saldos ASSET = Σ saldos LIABILITY + EQUITY, por moeda | consequência do anterior | teste de integração após funding + transferências |
| Projeção = recomputação a partir dos entries, para toda conta | mesma transação | teste de integração compara as duas |
| Saldo disponível de cliente nunca negativo (BR-016) | domínio + UPDATE condicional na projeção | teste de concorrência (Fase 2) |

### 7. Reservas (holds) não são lançamentos no MVP

Uma reserva **não gera journal**: é registrada em `ledger.holds` e somada em `reserved_minor_units`.
O journal só acontece quando a reserva é consumida (liquidação); a liberação apenas encerra o hold.
Ambas as transições são idempotentes por `transfer_id` (BR-042). O fluxo completo é detalhado na Fase 4.

## Alternativas consideradas

1. **Valor com sinal, sem lado** (`amount = -4000` / `+4000`, soma do journal = 0).
   Mais simples de somar, mas perde o vocabulário contábil, torna ambígua a leitura de contas de naturezas
   diferentes ("−10000 no caixa" é bom ou ruim?) e conflita com o `DATA_MODEL.md` (`side`, `minor_units > 0`).
   Descartada; a soma com sinal continua útil **só** como consulta de verificação.
2. **Contas de cliente como ativo** (visão do correntista: débito diminui o saldo do cliente).
   Intuitivo para quem usa extrato, mas contábil e conceitualmente errado do ponto de vista do banco, e
   deixaria a contrapartida do funding sem natureza coerente. Descartada.
3. **Contrapartida do funding como `EQUITY` (capital aportado)**.
   Também fecha a equação, mas não modela "dinheiro que entrou"; o caixa (ASSET) espelha o que um depósito
   real faz e prepara a Fase 4 (liquidação externa entra/sai pelo caixa). Descartada para o MVP.
4. **Reserva como journal** (débito no cliente, crédito numa conta "valores reservados").
   Mais auditável no próprio ledger, mas dobra o número de journals por operação e complica estorno de
   reservas soltas. Descartada para o MVP; revisitar se a auditoria de holds se mostrar insuficiente.
5. **Saldo só calculado, sem projeção**. Correto por construção, mas caro e difícil de travar sob
   concorrência. Mantida a projeção, com verificação contra os entries.

## Consequências

- ✅ Toda movimentação é explicável por um journal balanceado; o caixa simulado mostra quanto dinheiro
  fictício existe no sistema.
- ✅ A API expõe saldos positivos sem lógica de inversão; a convenção fica só no ledger.
- ✅ Os mesmos invariantes viram testes puros (domínio) e consultas SQL (integração/reconciliação).
- ⚠️ Quem lê os entries precisa lembrar que **crédito aumenta** a conta do cliente (passivo). Mitigação:
  documentar nos testes e expor `side` + saldo normal juntos em qualquer extrato interno.
- ⚠️ Toda conta nova exige escolher um tipo; erro de tipo inverte o efeito dos lançamentos.
  Mitigação: tipo imutável após criação e testes de lançamento por tipo.
- ⚠️ Multimoeda e câmbio exigirão contas de conversão por moeda; o MVP rejeita journal multimoeda.

## Fora do escopo deste ADR

Retenção de auditoria, exclusão lógica/encerramento de contas (BR-005), política de bloqueios judiciais
e taxas. Cada um recebe ADR próprio quando entrar no roadmap.

## Quando revisar

- Introdução de taxas, juros ou tarifas (contas `INCOME`/`EXPENSE`).
- Multimoeda ou câmbio.
- Liquidação externa (Pix/Fase 4) exigir contas institucionais adicionais.
- Necessidade de auditar reservas dentro do próprio ledger (alternativa 4).
