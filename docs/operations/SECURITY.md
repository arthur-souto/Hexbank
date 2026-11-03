# Segurança para o laboratório

## Modelo de ameaça
Ataques de repetição de requisição, acesso a conta alheia, spoofing de callbacks, brute force, payload inválido, logs com dados sensíveis e transferências simultâneas. Mesmo sendo ambiente de estudo, não retirar validação de autorização dos casos de uso.

## Regras
1. Não confiar em `ownerId` no corpo da requisição; principal vem do contexto autenticado.
2. Controllers validam formato; application/domain validam autorização e invariantes.
3. Rotas `/v1/dev/**` só existem e estão habilitadas em `dev`/`test`; bloqueadas por configuração **e** controle de papel de teste.
4. Segredos fora do repositório; `.env` local não versionado; exemplos em `.env.example` sem valores reais.
5. JWT (fase posterior ao primeiro esqueleto) com expiração, issuer/audience e assinatura; autorização não fica apenas no Gateway futuro.
6. Rate limit protege tráfego mas não substitui idempotência nem autorização.
7. TLS para qualquer ambiente acessível por rede; credenciais DB específicas e mínimo privilégio.
8. Não coletar PAN/CVV reais nem armazenar tokens de acesso em logs; payload de erro não expõe stack trace ou SQL.
9. Webhook futuro com HMAC/timestamp, identificação do evento e política anti-replay.
10. Auditoria estruturada com `requestId`, `actorId`, `operationId`, alteração de estado e sem dados secretos.

## Estado atual (Fase 0)
- Identidade simulada (`DevHeaderAuthenticationFilter`) só é registrada com profile `dev` ou `test`; teste `DefaultProfileSecurityTest` garante que sem profile o header é ignorado.
- API stateless: sem sessão, CSRF, form login ou HTTP Basic; nenhum usuário/senha padrão do Boot é criado.
- `/v1/dev/**` exige `ROLE_TEST_ADMIN`. Na Fase 1 o controller de funding também terá `@Profile({"dev","test"})` (defesa em profundidade, regra 3).
- Swagger/OpenAPI desligado sem profile.
- 401/403 respondidos em `problem+json` sem stack trace; `X-Request-Id` validado contra injeção em logs.
- `application-dev.yaml` mostra detalhes do health e loga SQL/Spring Security em DEBUG: **nunca** usar esse profile fora da máquina local.
- Credenciais locais via variáveis (`api/.env.example`); `.env` está no `.gitignore`.

## Limite da simulação
API com dados financeiros reais e comunicação com sistema de pagamentos exigem requisitos regulatórios, operacionais e de segurança fora do escopo. Ambiente dev/test é obrigatoriamente isolado.
