# ADR-0001 — Monólito modular e arquitetura hexagonal

- **Status:** aceito como decisão inicial de projeto (2026-11-01).
- **Contexto:** laboratório quer estudar mecanismos financeiros corretos antes das complexidades operacionais da distribuição. O documento original previa `services/` e `LedgerHttpAdapter`.
- **Decisão:** uma aplicação Spring Boot implantável com módulos de domínio, portas Java e PostgreSQL único. Cada módulo usa arquitetura hexagonal e contratos explícitos. HTTP somente na borda, não entre módulos internos.
- **Alternativas consideradas:** microsserviços desde o primeiro dia (descartados por custo e dificuldade de garantir transações); monólito sem módulos (descartado pelo acoplamento).
- **Consequências:** transações ACID e debugging mais simples; exige disciplina de fronteiras. Evolução para microsserviços demandará revisar atomicidade, integração e consistência.
- **Revisitar quando:** equipes/deploy independentes, escala assimétrica demonstrada, ou necessidade clara de isolamento operacional.
