# Modelo de especificação de implementação

Use este modelo para todo tipo de domínio ou caso de uso novo, dentro do documento de arquitetura do módulo (hoje, o [`ARCHITECTURE.md`](../architecture/ARCHITECTURE.md)). Exemplos completos: [`BalanceProjection`](../architecture/ARCHITECTURE.md#balanceprojection) e [API pública do ledger](../architecture/ARCHITECTURE.md#api-pública-do-ledger-applicationportin).

**Regras do modelo**
- Toda regra cita a sua origem com link: `BR-xxx` em [BUSINESS_RULES](../domain/BUSINESS_RULES.md), ADR ou contrato.
- Todo critério de aceite tem **resultado esperado concreto** (números, status HTTP, `code`, o que fica gravado).
- Cada critério vira pelo menos um teste. Os IDs (`LED-BP-05 #3`) podem ir no nome ou num comentário do teste.
- Recusa de negócio → `BusinessException` + `code` `4xx`. Erro de programação → `IllegalArgumentException`/`IllegalStateException` → `500`.
- Quando implementado: trocar o status para ✅, citar o arquivo e o teste e corrigir o texto se a implementação divergir.

---

```markdown
### `NomeDoTipo`
**Status:** ⏳ a implementar | ✅ implementado (`modulo/domain/NomeDoTipo.java`; testes em `NomeDoTipoTest`).
Regras: **[BR-xxx](../domain/BUSINESS_RULES.md#secao)** (resumo em 3 palavras), [ADR-000x](../adr/...). Tabela: `nome_tabela` ([DATA_MODEL](DATA_MODEL.md)).
Depende de: [OutroTipo](#outrotipo).

Uma ou duas frases: o que é e por que existe.

**Campos**

| Campo | Tipo | Regra |
|---|---|---|
| `campo` | `TipoJava` | invariante (não nulo, faixa, imutável…) |

**Contrato**

    // assinaturas públicas, retorno e exceções lançadas
    public static NomeDoTipo fabrica(...);
    public NomeDoTipo operacao(...);   // ExcecaoDeNegocio (409 CODE); bug → 500

Para casos de uso com HTTP: método, path, headers, corpo de entrada, status e corpo de saída.

#### PFX-01 — Título curto da história
> **Como** <ator>, **quero** <capacidade>, **para** <benefício de negócio> (BR-xxx).

| # | Dado | Quando | Então |
|---|---|---|---|
| 1 | estado inicial concreto | ação | resultado esperado concreto |
| 2 | caso de borda (limite exato) | ação | resultado |
| 3 | caso inválido | ação | exceção / status + `code`; o que **não** é gravado |

**Fora do escopo:** o que fica para depois, e em que fase.
**Pronto quando:** quais testes (unitário, `@WebMvcTest`, integração) cobrem quais histórias.
```

**Prefixos de ID em uso:** `ACC-UC` (casos de uso de `accounts`), `LED-BP` (`BalanceProjection`), `LED-UC` (API do ledger), `FUN` (funding), `SHR-IK` (`IdempotencyKey`), `SHR-AU` (auditoria). Para módulos novos: `TRF` (transfers), `IDP` (idempotência persistente), `OUT` (outbox) e assim por diante.
