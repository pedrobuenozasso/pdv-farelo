# Farelo OS

Sistema próprio de operação para uma cafeteria: PDV, cardápio QR, comandas, impressão,
estoque, operação de cozinha, notificações e (futuramente) emissão fiscal.

## Estrutura

```
apps/
  web/          Next.js — pedido.farelo.com.br, app.farelo.com.br (/pdv, /kitchen, /admin)
  api/          Spring Boot (Java 21) — api.farelo.com.br, modular monolith
  edge-agent/   Serviço local (mini PC) responsável por impressão física
docs/           Documentação de arquitetura, domínio, API e ADRs
infra/          Docker Compose, Caddy e configuração de deploy
```

## Arquitetura

Modular Monolith. Sem microserviços neste momento. Veja [`docs/architecture.md`](docs/architecture.md)
e [`docs/decisions/`](docs/decisions/) para detalhes e decisões registradas.

## Stack

- **Backend**: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, Flyway,
  PostgreSQL, Redis, Maven, JUnit 5, Testcontainers.
- **Frontend**: Next.js, React, TypeScript, Tailwind CSS, shadcn/ui.
- **Infra**: Docker, Docker Compose, Ubuntu LTS, Hostinger VPS, Cloudflare, Caddy, GitHub Actions.

## Desenvolvimento

Este projeto é construído em tickets pequenos e incrementais (ver `docs/architecture.md`
para o roadmap de épicos). Cada ticket tem escopo único e commits seguem
[Conventional Commits](https://www.conventionalcommits.org/).

### Rodando o projeto completo localmente

```bash
cp infra/.env.example infra/.env   # primeira vez apenas
cd infra
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

Sobe Postgres + backend (`:8080`) + frontend (`:3000`) num único comando —
`docker-compose.yml` sozinho sobe só o Postgres (uso mínimo, ex: rodar o
backend nativamente fora de container). Ver [`infra/README.md`](infra/README.md)
para detalhes (rebuild após mudança no backend, hot-reload do frontend, etc).
Isso é ambiente de **desenvolvimento** — não é o compose de produção (sem
ticket de deploy no roadmap atual, ver `docs/architecture.md`).
