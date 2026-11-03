# Infraestrutura local

Na Fase 0 a infraestrutura vive em [`api/docker-compose.yml`](../api/docker-compose.yml) e contém **apenas PostgreSQL 16** (porta `5433`, com healthcheck). Variáveis opcionais em `api/.env.example`.

```bash
cd api
docker compose up -d --wait   # sobe e espera o healthcheck
docker compose down -v        # derruba e apaga o volume
```

Broker (Fase 3), simulador Pix (Fase 4) e Redis (Fase 6) serão adicionados nas fases apropriadas. Quando houver mais de um serviço auxiliar, avaliar mover o compose para este diretório.
